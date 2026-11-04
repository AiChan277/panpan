package app.panyaya.model;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.supplier.GoodsReceipt;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.model.user.User;
import app.panyaya.model.waste.WasteRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the bakery has recorded, in one object. This is exactly what is written to the JSON
 * data file ({@code pan-ya-ya.json}): one field here = one property in the file.
 *
 * <p>Course topic <b>Generics &amp; Collections</b>: the data is a set of {@code List<...>}
 * collections, id sequences in a {@code Map<String, Long>}, and the generic helpers
 * {@link #findById} and {@link #indexById} work for any entity type.</p>
 */
public class BakeryData {

    public static final String FORMAT = "pan-ya-ya/data";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.BASIC_ISO_DATE; // 20261104

    // ---- file envelope ----
    private String format = FORMAT;
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private String appVersion;
    private long saveCount;
    private Instant savedAt;
    private String lastOperation;

    // ---- configuration ----
    private BakerySettings settings = new BakerySettings();
    /** Next id per entity type and daily counters for document numbers. Sorted for a stable file. */
    private Map<String, Long> sequences = new TreeMap<>();

    // ---- master data ----
    private List<User> users = new ArrayList<>();
    private List<ProductCategory> categories = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    private List<Ingredient> ingredients = new ArrayList<>();
    private List<Supplier> suppliers = new ArrayList<>();
    private List<Recipe> recipes = new ArrayList<>();

    // ---- stock ----
    private List<IngredientLot> ingredientLots = new ArrayList<>();
    private List<FinishedGoodsBatch> finishedGoodsBatches = new ArrayList<>();

    // ---- operations ----
    private List<GoodsReceipt> goodsReceipts = new ArrayList<>();
    private List<ProductionOrder> productionOrders = new ArrayList<>();
    private List<Sale> sales = new ArrayList<>();
    private List<WasteRecord> wasteRecords = new ArrayList<>();
    /** The append-only stock ledger. */
    private List<InventoryMovement> movements = new ArrayList<>();
    private List<AuditEntry> auditLog = new ArrayList<>();

    /** Returns the next unused id for an entity type. Ids are never reused. */
    public long nextId(String entityType) {
        long next = sequences.getOrDefault(entityType, 0L) + 1;
        sequences.put(entityType, next);
        return next;
    }

    /** Business document number with a daily counter, e.g. nextNumber("S", 2026-11-04) = "S-20261104-0001". */
    public String nextNumber(String prefix, LocalDate date) {
        String key = prefix + "-" + NUMBER_DATE.format(date);
        long next = sequences.getOrDefault(key, 0L) + 1;
        sequences.put(key, next);
        return String.format("%s-%04d", key, next);
    }

    /** Called by the DataStore just before writing the file. */
    public void markSaved(String operation, Instant at, String version) {
        this.saveCount++;
        this.savedAt = at;
        this.lastOperation = operation;
        this.appVersion = version;
    }

    /** Generic lookup by id, for any entity list. Returns null when not found. */
    public static <T extends BaseEntity> T findById(List<T> list, long id) {
        for (T entity : list) {
            if (entity.getId() == id) {
                return entity;
            }
        }
        return null;
    }

    /** Like {@link #findById} but throws a friendly error when the entity does not exist. */
    public static <T extends BaseEntity> T require(List<T> list, long id, String label) throws ValidationException {
        T entity = findById(list, id);
        if (entity == null) {
            throw new ValidationException(label + " #" + id + " does not exist.");
        }
        return entity;
    }

    /** Builds an id -> entity map for fast repeated lookups (reports, validation). */
    public static <T extends BaseEntity> Map<Long, T> indexById(List<T> list) {
        Map<Long, T> index = new HashMap<>();
        for (T entity : list) {
            index.put(entity.getId(), entity);
        }
        return index;
    }

    // ---- getters (the lists are the live collections; only services inside a DataStore
    //      transaction should modify them) ----

    public String getFormat() {
        return format;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public long getSaveCount() {
        return saveCount;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public String getLastOperation() {
        return lastOperation;
    }

    public BakerySettings getSettings() {
        return settings;
    }

    public Map<String, Long> getSequences() {
        return sequences;
    }

    public List<User> getUsers() {
        return users;
    }

    public List<ProductCategory> getCategories() {
        return categories;
    }

    public List<Product> getProducts() {
        return products;
    }

    public List<Ingredient> getIngredients() {
        return ingredients;
    }

    public List<Supplier> getSuppliers() {
        return suppliers;
    }

    public List<Recipe> getRecipes() {
        return recipes;
    }

    public List<IngredientLot> getIngredientLots() {
        return ingredientLots;
    }

    public List<FinishedGoodsBatch> getFinishedGoodsBatches() {
        return finishedGoodsBatches;
    }

    public List<GoodsReceipt> getGoodsReceipts() {
        return goodsReceipts;
    }

    public List<ProductionOrder> getProductionOrders() {
        return productionOrders;
    }

    public List<Sale> getSales() {
        return sales;
    }

    public List<WasteRecord> getWasteRecords() {
        return wasteRecords;
    }

    public List<InventoryMovement> getMovements() {
        return movements;
    }

    public List<AuditEntry> getAuditLog() {
        return auditLog;
    }
}
