package app.panyaya.persistence;

import app.panyaya.model.BakeryData;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.inventory.StockLot;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.production.ProductionStatus;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.recipe.RecipeItem;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleLine;
import app.panyaya.model.sales.SaleStatus;
import app.panyaya.model.user.Role;
import app.panyaya.model.user.User;
import app.panyaya.model.waste.WasteRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Checks the whole data set for consistency. A JSON file has no database constraints, so these
 * rules are the "second wall" behind the business methods: they run before every save and every
 * time a file is loaded or restored. If any rule fails, nothing is saved.
 */
public class DataValidator {

    public List<String> validate(BakeryData data) {
        List<String> errors = new ArrayList<>();
        checkIds(data, errors);
        checkUniqueKeys(data, errors);
        checkMasterDataReferences(data, errors);
        checkRecipes(data, errors);
        checkStockAndLedger(data, errors);
        checkSales(data, errors);
        checkProduction(data, errors);
        checkWaste(data, errors);
        checkUsers(data, errors);
        return errors;
    }

    // ---- V-01 unique ids --------------------------------------------------------------------

    private void checkIds(BakeryData data, List<String> errors) {
        Map<String, Long> sequences = data.getSequences();
        checkUniqueIds(EntityTypes.USER, data.getUsers(), sequences, errors);
        checkUniqueIds(EntityTypes.CATEGORY, data.getCategories(), sequences, errors);
        checkUniqueIds(EntityTypes.PRODUCT, data.getProducts(), sequences, errors);
        checkUniqueIds(EntityTypes.INGREDIENT, data.getIngredients(), sequences, errors);
        checkUniqueIds(EntityTypes.SUPPLIER, data.getSuppliers(), sequences, errors);
        checkUniqueIds(EntityTypes.RECIPE, data.getRecipes(), sequences, errors);
        checkUniqueIds(EntityTypes.INGREDIENT_LOT, data.getIngredientLots(), sequences, errors);
        checkUniqueIds(EntityTypes.FG_BATCH, data.getFinishedGoodsBatches(), sequences, errors);
        checkUniqueIds(EntityTypes.RECEIPT, data.getGoodsReceipts(), sequences, errors);
        checkUniqueIds(EntityTypes.PRODUCTION, data.getProductionOrders(), sequences, errors);
        checkUniqueIds(EntityTypes.SALE, data.getSales(), sequences, errors);
        checkUniqueIds(EntityTypes.WASTE, data.getWasteRecords(), sequences, errors);
        checkUniqueIds(EntityTypes.MOVEMENT, data.getMovements(), sequences, errors);
        checkUniqueIds(EntityTypes.AUDIT, data.getAuditLog(), sequences, errors);
    }

    /**
     * Generic: works for any list of entities. Ids must be positive, unique, and not above the
     * sequence counter (otherwise the next new record would reuse an existing id).
     */
    private <T extends BaseEntity> void checkUniqueIds(String type, List<T> list, Map<String, Long> sequences,
                                                       List<String> errors) {
        Set<Long> seen = new HashSet<>();
        long counter = sequences.getOrDefault(type, 0L);
        for (T entity : list) {
            if (entity == null) {
                errors.add("Empty " + type + " record");
                continue;
            }
            if (entity.getId() <= 0) {
                errors.add(type + " has a non-positive id " + entity.getId());
            }
            if (!seen.add(entity.getId())) {
                errors.add("Duplicate " + type + " id " + entity.getId());
            }
            if (entity.getId() > counter) {
                errors.add(type + " id " + entity.getId() + " is above its sequence counter " + counter);
            }
        }
    }

    // ---- V-02 unique business keys ----------------------------------------------------------

    private void checkUniqueKeys(BakeryData data, List<String> errors) {
        checkUniqueText("product SKU", data.getProducts(), Product::getSku, errors);
        checkUniqueText("ingredient code", data.getIngredients(), Ingredient::getCode, errors);
        checkUniqueText("username", data.getUsers(), User::getUsername, errors);
        checkUniqueText("category name", data.getCategories(), ProductCategory::getName, errors);
        checkUniqueText("sale number", data.getSales(), Sale::getSaleNo, errors);
        checkUniqueText("production order number", data.getProductionOrders(), ProductionOrder::getOrderNo, errors);
        checkUniqueText("waste number", data.getWasteRecords(), WasteRecord::getWasteNo, errors);
    }

    private <T> void checkUniqueText(String label, List<T> list, Function<T, String> key, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (T item : list) {
            String value = key.apply(item);
            if (value == null || value.isEmpty()) {
                errors.add("Missing " + label);
                continue;
            }
            if (!seen.add(value.toLowerCase(Locale.ROOT))) {
                errors.add("Duplicate " + label + " \"" + value + "\"");
            }
        }
    }

    // ---- V-03 references --------------------------------------------------------------------

    private void checkMasterDataReferences(BakeryData data, List<String> errors) {
        Map<Long, ProductCategory> categories = BakeryData.indexById(data.getCategories());
        for (Product product : data.getProducts()) {
            if (!categories.containsKey(product.getCategoryId())) {
                errors.add("Product " + product.getSku() + " refers to missing category " + product.getCategoryId());
            }
            if (!Money.isPositive(product.getSellingPrice())) {
                errors.add("Product " + product.getSku() + " has no positive price");
            }
        }
        Set<Long> supplierIds = BakeryData.indexById(data.getSuppliers()).keySet();
        for (Ingredient ingredient : data.getIngredients()) {
            if (ingredient.getDefaultSupplierId() != null && !supplierIds.contains(ingredient.getDefaultSupplierId())) {
                errors.add("Ingredient " + ingredient.getCode() + " refers to missing supplier");
            }
        }
    }

    // ---- V-04 recipes -----------------------------------------------------------------------

    private void checkRecipes(BakeryData data, List<String> errors) {
        Map<Long, Product> products = BakeryData.indexById(data.getProducts());
        Map<Long, Ingredient> ingredients = BakeryData.indexById(data.getIngredients());
        for (Recipe recipe : data.getRecipes()) {
            Product product = products.get(recipe.getProductId());
            if (product == null) {
                errors.add("Recipe " + recipe.getName() + " refers to missing product " + recipe.getProductId());
            } else if (!product.isManufactured()) {
                errors.add("Recipe " + recipe.getName() + " belongs to a resale product");
            }
            if (!Quantities.isPositive(recipe.getYieldQuantity())) {
                errors.add("Recipe " + recipe.getName() + " has no positive yield");
            }
            for (RecipeItem item : recipe.getItems()) {
                Ingredient ingredient = ingredients.get(item.getIngredientId());
                if (ingredient == null) {
                    errors.add("Recipe " + recipe.getName() + " refers to missing ingredient " + item.getIngredientId());
                } else if (!item.getUnit().isCompatibleWith(ingredient.getBaseUnit())) {
                    errors.add("Recipe " + recipe.getName() + ": unit " + item.getUnit() + " incompatible with "
                            + ingredient.getName());
                }
            }
        }
    }

    // ---- V-05 / V-06 / V-07 stock bounds and ledger balance ----------------------------------

    private void checkStockAndLedger(BakeryData data, List<String> errors) {
        Map<Long, IngredientLot> ingredientLots = BakeryData.indexById(data.getIngredientLots());
        Map<Long, FinishedGoodsBatch> batches = BakeryData.indexById(data.getFinishedGoodsBatches());
        Set<Long> ingredientIds = BakeryData.indexById(data.getIngredients()).keySet();
        Set<Long> productIds = BakeryData.indexById(data.getProducts()).keySet();

        // sum of signed movement quantities per lot = what the lot should contain
        Map<Long, BigDecimal> ingredientBalance = new HashMap<>();
        Map<Long, BigDecimal> productBalance = new HashMap<>();
        for (InventoryMovement movement : data.getMovements()) {
            if (!Quantities.isPositive(movement.getQuantity())) {
                errors.add("Movement " + movement.getId() + " has a non-positive quantity");
            }
            Map<Long, BigDecimal> balance;
            StockLot lot;
            if (movement.getStockKind() == StockKind.INGREDIENT) {
                balance = ingredientBalance;
                lot = ingredientLots.get(movement.getLotId());
            } else {
                balance = productBalance;
                lot = batches.get(movement.getLotId());
            }
            if (lot == null) {
                errors.add("Movement " + movement.getId() + " refers to missing lot " + movement.getLotId());
                continue;
            }
            if (lot.getItemId() != movement.getItemId()) {
                errors.add("Movement " + movement.getId() + " item does not match its lot");
            }
            BigDecimal previous = balance.getOrDefault(movement.getLotId(), BigDecimal.ZERO);
            balance.put(movement.getLotId(), previous.add(movement.getSignedQuantity()));
        }
        for (IngredientLot lot : data.getIngredientLots()) {
            checkLot(lot, ingredientIds, ingredientBalance, errors);
        }
        for (FinishedGoodsBatch batch : data.getFinishedGoodsBatches()) {
            checkLot(batch, productIds, productBalance, errors);
        }
    }

    private void checkLot(StockLot lot, Set<Long> itemIds, Map<Long, BigDecimal> balance, List<String> errors) {
        if (!itemIds.contains(lot.getItemId())) {
            errors.add("Lot " + lot.getLotCode() + " refers to missing item " + lot.getItemId());
        }
        if (lot.getRemainingQuantity().signum() < 0) {
            errors.add("Lot " + lot.getLotCode() + " has negative stock");
        }
        BigDecimal expected = balance.getOrDefault(lot.getId(), BigDecimal.ZERO);
        if (expected.compareTo(lot.getRemainingQuantity()) != 0) {
            errors.add("Lot " + lot.getLotCode() + " holds " + lot.getRemainingQuantity().toPlainString()
                    + " but its movements add up to " + expected.toPlainString());
        }
    }

    // ---- V-08 sales -------------------------------------------------------------------------

    private void checkSales(BakeryData data, List<String> errors) {
        Set<Long> batchIds = BakeryData.indexById(data.getFinishedGoodsBatches()).keySet();
        for (Sale sale : data.getSales()) {
            String no = sale.getSaleNo();
            if (sale.getStatus() == SaleStatus.OPEN) {
                errors.add("Sale " + no + " was saved while still open");
            }
            if (sale.getLines().isEmpty()) {
                errors.add("Sale " + no + " has no lines");
            }
            BigDecimal subtotal = Money.ZERO;
            for (SaleLine line : sale.getLines()) {
                BigDecimal expectedLine = Money.multiply(line.getUnitPrice(), Quantities.of(line.getQuantity()));
                if (expectedLine.compareTo(line.getLineTotal()) != 0) {
                    errors.add("Sale " + no + ": line total of " + line.getNameSnapshot() + " is wrong");
                }
                if (line.getAllocatedQuantity().compareTo(Quantities.of(line.getQuantity())) != 0) {
                    errors.add("Sale " + no + ": allocations of " + line.getNameSnapshot() + " do not match quantity");
                }
                for (app.panyaya.model.sales.SaleAllocation allocation : line.getAllocations()) {
                    if (!batchIds.contains(allocation.getBatchId())) {
                        errors.add("Sale " + no + " refers to missing batch " + allocation.getBatchId());
                    }
                }
                subtotal = subtotal.add(line.getLineTotal());
            }
            if (Money.normalize(subtotal).compareTo(sale.getSubtotal()) != 0
                    || sale.getSubtotal().compareTo(sale.getTotal()) != 0) {
                errors.add("Sale " + no + ": totals do not add up");
            }
            if (sale.getPaymentMethod() == PaymentMethod.CASH && sale.getAmountTendered() != null) {
                BigDecimal change = Money.normalize(sale.getAmountTendered().subtract(sale.getTotal()));
                if (change.compareTo(sale.getChangeGiven()) != 0) {
                    errors.add("Sale " + no + ": change does not equal tendered - total");
                }
            }
            if (sale.getStatus() == SaleStatus.VOIDED && (sale.getVoidReason() == null || sale.getVoidedAt() == null)) {
                errors.add("Sale " + no + " is voided without a reason");
            }
        }
    }

    // ---- V-09 production --------------------------------------------------------------------

    private void checkProduction(BakeryData data, List<String> errors) {
        Set<Long> batchIds = BakeryData.indexById(data.getFinishedGoodsBatches()).keySet();
        for (ProductionOrder order : data.getProductionOrders()) {
            if (order.getStatus() == ProductionStatus.COMPLETED) {
                if (order.getFinishedBatchId() == null || !batchIds.contains(order.getFinishedBatchId())) {
                    errors.add("Production " + order.getOrderNo() + " is completed but has no finished batch");
                }
                if (order.getGoodQuantity() < 1) {
                    errors.add("Production " + order.getOrderNo() + " is completed with zero good pieces");
                }
            }
        }
    }

    // ---- V-10 waste -------------------------------------------------------------------------

    private void checkWaste(BakeryData data, List<String> errors) {
        Set<Long> lotIds = BakeryData.indexById(data.getIngredientLots()).keySet();
        Set<Long> batchIds = BakeryData.indexById(data.getFinishedGoodsBatches()).keySet();
        for (WasteRecord waste : data.getWasteRecords()) {
            if (!Quantities.isPositive(waste.getQuantity())) {
                errors.add("Waste " + waste.getWasteNo() + " has no positive quantity");
            }
            if (Money.isNegative(waste.getEstimatedLoss())) {
                errors.add("Waste " + waste.getWasteNo() + " has a negative loss");
            }
            if (waste.getLotId() != null) {
                Set<Long> ids = waste.getStockKind() == StockKind.INGREDIENT ? lotIds : batchIds;
                if (!ids.contains(waste.getLotId())) {
                    errors.add("Waste " + waste.getWasteNo() + " refers to missing lot " + waste.getLotId());
                }
            }
        }
    }

    // ---- V-11 users -------------------------------------------------------------------------

    private void checkUsers(BakeryData data, List<String> errors) {
        if (data.getUsers().isEmpty()) {
            return; // brand-new file before the setup wizard has finished
        }
        boolean activeOwner = false;
        for (User user : data.getUsers()) {
            if (user.isActive() && user.getRole() == Role.OWNER) {
                activeOwner = true;
            }
            if (user.getPasswordHash() == null || !user.getPasswordHash().startsWith("pbkdf2-sha256$")) {
                errors.add("User " + user.getUsername() + " has an unrecognised password hash");
            }
        }
        if (!activeOwner) {
            errors.add("There must be at least one active Owner account");
        }
    }
}
