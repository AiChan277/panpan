package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Products and product categories. */
public class ProductService extends BaseService {

    public ProductService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    // ---- categories -------------------------------------------------------------------------

    public List<ProductCategory> getCategories(boolean includeInactive) {
        List<ProductCategory> result = new ArrayList<>();
        for (ProductCategory category : data().getCategories()) {
            if (includeInactive || category.isActive()) {
                result.add(category);
            }
        }
        result.sort(Comparator.comparing(ProductCategory::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public String categoryName(long categoryId) {
        ProductCategory category = BakeryData.findById(data().getCategories(), categoryId);
        return category == null ? "?" : category.getName();
    }

    public ProductCategory createCategory(String name) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS);
        return store.execute("CREATE_CATEGORY", data -> {
            ensureCategoryNameFree(data, name, 0);
            ProductCategory category = new ProductCategory(data.nextId(EntityTypes.CATEGORY), name);
            data.getCategories().add(category);
            audit(data, "CREATE_CATEGORY", category.getName());
            return category;
        });
    }

    public void renameCategory(long categoryId, String newName) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS);
        store.execute("RENAME_CATEGORY", data -> {
            ensureCategoryNameFree(data, newName, categoryId);
            ProductCategory category = BakeryData.require(data.getCategories(), categoryId, "Category");
            category.rename(newName);
            audit(data, "RENAME_CATEGORY", category.getName());
            return null;
        });
    }

    public void setCategoryActive(long categoryId, boolean active) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS);
        store.execute("CATEGORY_ACTIVE", data -> {
            BakeryData.require(data.getCategories(), categoryId, "Category").setActive(active);
            return null;
        });
    }

    private static void ensureCategoryNameFree(BakeryData data, String name, long exceptId) throws ValidationException {
        for (ProductCategory category : data.getCategories()) {
            if (category.getId() != exceptId && TextUtil.sameKey(category.getName(), name)) {
                throw new ValidationException("Category \"" + TextUtil.normalize(name) + "\" already exists.");
            }
        }
    }

    // ---- products ---------------------------------------------------------------------------

    /** Search by partial name or SKU (case-insensitive) with optional filters. */
    public List<Product> search(String text, Long categoryId, ProductType type, boolean includeArchived) {
        List<Product> result = new ArrayList<>();
        for (Product product : data().getProducts()) {
            boolean textMatches = TextUtil.containsIgnoreCase(product.getName(), text)
                    || TextUtil.containsIgnoreCase(product.getSku(), text);
            boolean categoryMatches = categoryId == null || product.getCategoryId() == categoryId;
            boolean typeMatches = type == null || product.getType() == type;
            boolean statusMatches = includeArchived || product.isActive();
            if (textMatches && categoryMatches && typeMatches && statusMatches) {
                result.add(product);
            }
        }
        result.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public List<Product> getActiveProducts() {
        return search("", null, null, false);
    }

    public List<Product> getActiveManufacturedProducts() {
        return search("", null, ProductType.MANUFACTURED, false);
    }

    public List<Product> getActiveResaleProducts() {
        return search("", null, ProductType.RESALE, false);
    }

    public Product getProduct(long productId) {
        return BakeryData.findById(data().getProducts(), productId);
    }

    public String suggestSku(String productName, Long categoryId) {
        String category = categoryId == null ? "" : categoryName(categoryId);
        String base = TextUtil.suggestSku(category, productName);
        String candidate = base;
        int counter = 2;
        while (skuTaken(data(), candidate, 0)) {
            String suffix = "-" + counter++;
            candidate = (base.length() + suffix.length() > 20 ? base.substring(0, 20 - suffix.length()) : base) + suffix;
        }
        return candidate;
    }

    public Product createProduct(String name, String sku, long categoryId, ProductType type, BigDecimal price,
                                 int shelfLifeHours, String description) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS);
        return store.execute("CREATE_PRODUCT", data -> {
            requireActiveCategory(data, categoryId);
            Product product = new Product(data.nextId(EntityTypes.PRODUCT), name, sku, categoryId, type, price,
                    shelfLifeHours, description, now());
            if (skuTaken(data, product.getSku(), 0)) {
                throw new ValidationException("SKU \"" + product.getSku() + "\" is already used by another product.");
            }
            data.getProducts().add(product);
            audit(data, "CREATE_PRODUCT", product.getSku() + " " + product.getName());
            return product;
        });
    }

    public Product updateProduct(long productId, String name, String sku, long categoryId, ProductType type,
                                 BigDecimal price, int shelfLifeHours, String description)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS);
        return store.execute("UPDATE_PRODUCT", data -> {
            Product product = BakeryData.require(data.getProducts(), productId, "Product");
            if (product.getCategoryId() != categoryId) {
                requireActiveCategory(data, categoryId);
            }
            if (skuTaken(data, TextUtil.normalizeCode(sku), productId)) {
                throw new ValidationException("SKU \"" + TextUtil.normalizeCode(sku) + "\" is already used by another product.");
            }
            if (product.getType() != type) {
                if (!StockQueries.batchesOf(data, productId).isEmpty() || hasRecipe(data, productId)) {
                    throw new ValidationException("The product type cannot change after stock or a recipe exists.");
                }
                product.changeType(type);
            }
            product.updateDetails(name, sku, categoryId, shelfLifeHours, description);
            product.changePrice(price, userId(), now());
            audit(data, "UPDATE_PRODUCT", product.getSku());
            return product;
        });
    }

    /** Archive instead of delete: sales and production history still reference the product. */
    public void setArchived(long productId, boolean archived) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS);
        store.execute(archived ? "ARCHIVE_PRODUCT" : "RESTORE_PRODUCT", data -> {
            Product product = BakeryData.require(data.getProducts(), productId, "Product");
            if (archived) {
                product.archive();
            } else {
                product.restore();
            }
            audit(data, archived ? "ARCHIVE_PRODUCT" : "RESTORE_PRODUCT", product.getSku());
            return null;
        });
    }

    private static void requireActiveCategory(BakeryData data, long categoryId) throws ValidationException {
        ProductCategory category = BakeryData.findById(data.getCategories(), categoryId);
        if (category == null || !category.isActive()) {
            throw new ValidationException("Please choose an active category.");
        }
    }

    static boolean skuTaken(BakeryData data, String sku, long exceptProductId) {
        for (Product product : data.getProducts()) {
            if (product.getId() != exceptProductId && product.getSku().equalsIgnoreCase(sku)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRecipe(BakeryData data, long productId) {
        for (Recipe recipe : data.getRecipes()) {
            if (recipe.getProductId() == productId) {
                return true;
            }
        }
        return false;
    }
}
