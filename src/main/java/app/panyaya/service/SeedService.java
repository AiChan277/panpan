package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.DataCorruptedException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.recipe.RecipeItem;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports the starter recipe templates once. Existing codes and SKUs are skipped, nothing is
 * overwritten, and the imported recipes are DRAFT templates that must be reviewed before use.
 */
public class SeedService extends BaseService {

    public static final String STARTER_RESOURCE = "/app/panyaya/data/starter-recipes.json";

    public SeedService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    public boolean isStarterImported() {
        return data().getSettings().isStarterRecipesImported();
    }

    /** @return number of recipes created */
    public int importStarterRecipes() throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_SETTINGS);
        StarterData starter = readStarterFile();
        return store.execute("IMPORT_STARTER_RECIPES", data -> {
            if (data.getSettings().isStarterRecipesImported()) {
                throw new ValidationException("The starter recipes were already imported once.");
            }
            Map<String, Long> ingredientIds = new HashMap<>();
            for (Ingredient existing : data.getIngredients()) {
                ingredientIds.put(existing.getCode(), existing.getId());
            }
            for (StarterData.StarterIngredient si : starter.ingredients) {
                if (ingredientIds.containsKey(si.code)) {
                    continue; // never overwrite the bakery's own data
                }
                Ingredient ingredient = new Ingredient(data.nextId(EntityTypes.INGREDIENT), si.code, si.name,
                        Unit.valueOf(si.baseUnit), Quantities.of(si.minimumStock), si.tracksExpiry, si.stockTracked,
                        null, "Imported from starter templates");
                data.getIngredients().add(ingredient);
                ingredientIds.put(ingredient.getCode(), ingredient.getId());
            }

            Map<String, Long> productIds = new HashMap<>();
            for (Product existing : data.getProducts()) {
                productIds.put(existing.getSku(), existing.getId());
            }
            for (StarterData.StarterProduct sp : starter.products) {
                if (productIds.containsKey(sp.sku)) {
                    continue;
                }
                ProductCategory category = findOrCreateCategory(data, sp.category);
                Product product = new Product(data.nextId(EntityTypes.PRODUCT), sp.name, sp.sku, category.getId(),
                        ProductType.MANUFACTURED, Money.of(sp.price), sp.shelfLifeHours, sp.description, now());
                data.getProducts().add(product);
                productIds.put(product.getSku(), product.getId());
            }

            int created = 0;
            for (StarterData.StarterRecipe sr : starter.recipes) {
                Long productId = productIds.get(sr.productSku);
                if (productId == null) {
                    continue;
                }
                List<RecipeItem> items = new ArrayList<>();
                for (StarterData.StarterItem item : sr.items) {
                    Long ingredientId = ingredientIds.get(item.ingredientCode);
                    if (ingredientId == null) {
                        throw new ValidationException("Starter file refers to unknown ingredient " + item.ingredientCode);
                    }
                    items.add(new RecipeItem(ingredientId, Quantities.of(item.quantity), Unit.valueOf(item.unit), item.note));
                }
                Recipe recipe = new Recipe(data.nextId(EntityTypes.RECIPE), sr.name, productId, Quantities.of(sr.yield),
                        items, sr.prepMinutes, sr.bakeMinutes, null, sr.instructions, now(), userId());
                recipe.markAsTemplate();
                data.getRecipes().add(recipe);
                created++;
            }
            data.getSettings().markStarterRecipesImported();
            audit(data, "IMPORT_STARTER_RECIPES", created + " template recipe(s)");
            return created;
        });
    }

    private StarterData readStarterFile() throws DataCorruptedException {
        try (InputStream in = SeedService.class.getResourceAsStream(STARTER_RESOURCE)) {
            if (in == null) {
                throw new DataCorruptedException("Starter recipe file is missing from the application.");
            }
            return store.getCodec().decodeObject(in, StarterData.class);
        } catch (IOException e) {
            throw new DataCorruptedException("Cannot read the starter recipe file: " + e.getMessage(), e);
        }
    }

    private static ProductCategory findOrCreateCategory(BakeryData data, String name) throws ValidationException {
        for (ProductCategory category : data.getCategories()) {
            if (TextUtil.sameKey(category.getName(), name)) {
                return category;
            }
        }
        ProductCategory category = new ProductCategory(data.nextId(EntityTypes.CATEGORY), name);
        data.getCategories().add(category);
        return category;
    }
}
