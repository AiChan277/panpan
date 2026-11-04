package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.io.CsvReader;
import app.panyaya.io.CsvRowMapper;
import app.panyaya.io.CsvWriter;
import app.panyaya.io.ImportError;
import app.panyaya.io.ImportPreview;
import app.panyaya.model.BakeryData;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleLine;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.model.user.Permission;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.report.DateRange;
import app.panyaya.report.Report;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import app.panyaya.util.TextUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CSV export (reports, master data, sales, ledger, waste) and CSV import of master data.
 *
 * <p>Import works in two steps: a <b>dry run</b> ({@code preview...}) that reads the file and reports
 * every invalid row with its line number while writing nothing, and a <b>commit</b> that imports all
 * rows in one save - or none if anything is wrong.</p>
 */
public class CsvService extends BaseService {

    public CsvService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    // ================================ EXPORT ================================================

    public void exportReport(Report report, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>(report.getRows());
        CsvWriter.write(file, report.getColumns(), rows);
    }

    public void exportProducts(Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Product p : data().getProducts()) {
            rows.add(Arrays.asList(p.getName(), p.getSku(), categoryName(p.getCategoryId()), p.getType().name(),
                    Formatters.plainMoney(p.getSellingPrice()), String.valueOf(p.getShelfLifeHours()),
                    p.getDescription(), p.isActive() ? "active" : "archived"));
        }
        CsvWriter.write(file, Arrays.asList("name", "sku", "category", "type", "price", "shelf_life_hours",
                "description", "status"), rows);
    }

    public void exportIngredients(Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Ingredient i : data().getIngredients()) {
            IngredientStock stock = IngredientService.stockOf(data(), i, now(), zone());
            rows.add(Arrays.asList(i.getCode(), i.getName(), i.getBaseUnit().name(),
                    Formatters.quantity(i.getMinimumStock()), yesNo(i.isTracksExpiry()), yesNo(i.isStockTracked()),
                    i.getNotes(), Formatters.quantity(stock.getUsableQuantity()), i.isActive() ? "active" : "inactive"));
        }
        CsvWriter.write(file, Arrays.asList("code", "name", "unit", "minimum_stock", "tracks_expiry",
                "stock_tracked", "notes", "usable_stock", "status"), rows);
    }

    public void exportSuppliers(Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Supplier s : data().getSuppliers()) {
            rows.add(Arrays.asList(s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(), s.getAddress(),
                    s.getNotes()));
        }
        CsvWriter.write(file, Arrays.asList("name", "contact_person", "phone", "email", "address", "notes"), rows);
    }

    /** One row per sale line, so the file can be pivoted in a spreadsheet. */
    public void exportSales(Path file, DateRange range) throws IOException {
        ZoneId zone = zone();
        List<List<String>> rows = new ArrayList<>();
        for (Sale sale : data().getSales()) {
            if (!range.contains(sale.getBusinessDate())) {
                continue;
            }
            for (SaleLine line : sale.getLines()) {
                rows.add(Arrays.asList(sale.getSaleNo(), Formatters.dateTime(sale.getOccurredAt(), zone),
                        sale.getCashierName(), sale.getStatus().name(), String.valueOf(sale.getPaymentMethod()),
                        line.getSkuSnapshot(), line.getNameSnapshot(), String.valueOf(line.getQuantity()),
                        Formatters.plainMoney(line.getUnitPrice()), Formatters.plainMoney(line.getLineTotal()),
                        Formatters.plainMoney(line.getCostTotal())));
            }
        }
        CsvWriter.write(file, Arrays.asList("sale_no", "time", "cashier", "status", "payment", "sku", "product",
                "quantity", "unit_price", "line_total", "cost"), rows);
    }

    public void exportMovements(Path file, DateRange range) throws IOException {
        ZoneId zone = zone();
        List<List<String>> rows = new ArrayList<>();
        for (InventoryMovement m : data().getMovements()) {
            if (!range.contains(m.getBusinessDate())) {
                continue;
            }
            rows.add(Arrays.asList(String.valueOf(m.getId()), Formatters.dateTime(m.getOccurredAt(), zone),
                    m.getTypeName(), m.getStockKind().name(), itemName(m.getStockKind(), m.getItemId()),
                    lotCode(m), m.getSignedQuantity().toPlainString(), Formatters.unitCost(m.getUnitCost()),
                    TextUtil.orDash(m.getReference()), TextUtil.orDash(m.getNote())));
        }
        CsvWriter.write(file, Arrays.asList("id", "time", "type", "kind", "item", "lot", "quantity", "unit_cost",
                "reference", "note"), rows);
    }

    public void exportWaste(Path file, DateRange range) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (WasteRecord w : data().getWasteRecords()) {
            if (!range.contains(w.getBusinessDate())) {
                continue;
            }
            rows.add(Arrays.asList(w.getWasteNo(), Formatters.ISO_DATE.format(w.getBusinessDate()), w.getStockKind().name(),
                    w.getItemName(), TextUtil.orDash(w.getLotCode()), w.getQuantity().toPlainString(),
                    w.getUnit().getSymbol(), w.getReason().name(), Formatters.plainMoney(w.getEstimatedLoss()),
                    w.getNote()));
        }
        CsvWriter.write(file, Arrays.asList("waste_no", "date", "kind", "item", "lot", "quantity", "unit", "reason",
                "estimated_loss", "note"), rows);
    }

    // ================================ IMPORT ================================================

    /** Product row before import. */
    public static class ProductRow {
        String name;
        String sku;
        String category;
        ProductType type;
        BigDecimal price;
        int shelfLifeHours;
        String description;

        public String getName() {
            return name;
        }

        public String getSku() {
            return sku;
        }
    }

    /** Ingredient row before import. */
    public static class IngredientRow {
        String code;
        String name;
        Unit unit;
        BigDecimal minimumStock;
        boolean tracksExpiry;
        boolean stockTracked;
        String notes;

        public String getName() {
            return name;
        }
    }

    /** Supplier row before import. */
    public static class SupplierRow {
        String name;
        String contact;
        String phone;
        String email;
        String address;
        String notes;

        public String getName() {
            return name;
        }
    }

    /** Generic dry run: parse, check the header, map every row and collect all errors. */
    public <T> ImportPreview<T> preview(Path file, CsvRowMapper<T> mapper) throws IOException {
        List<CsvReader.Row> csvRows = CsvReader.read(file);
        List<T> mapped = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        if (csvRows.isEmpty()) {
            errors.add(new ImportError(1, "", "The file has no data rows."));
            return new ImportPreview<>(mapped, errors, 0);
        }
        for (String column : mapper.requiredColumns()) {
            if (!csvRows.get(0).has(column)) {
                errors.add(new ImportError(1, column, "Required column \"" + column + "\" is missing in the header."));
            }
        }
        if (!errors.isEmpty()) {
            return new ImportPreview<>(mapped, errors, csvRows.size());
        }
        for (CsvReader.Row row : csvRows) {
            T item = mapper.map(row, errors);
            if (item != null) {
                mapped.add(item);
            }
        }
        return new ImportPreview<>(mapped, errors, csvRows.size());
    }

    public ImportPreview<ProductRow> previewProducts(Path file) throws IOException {
        return preview(file, new ProductRowMapper(data()));
    }

    public ImportPreview<IngredientRow> previewIngredients(Path file) throws IOException {
        return preview(file, new IngredientRowMapper(data()));
    }

    public ImportPreview<SupplierRow> previewSuppliers(Path file) throws IOException {
        return preview(file, new SupplierRowMapper(data()));
    }

    public int importProducts(ImportPreview<ProductRow> preview) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS);
        requireCommittable(preview);
        return store.execute("IMPORT_PRODUCTS", data -> {
            for (ProductRow row : preview.getRows()) {
                ProductCategory category = findOrCreateCategory(data, row.category);
                data.getProducts().add(new Product(data.nextId(EntityTypes.PRODUCT), row.name, row.sku,
                        category.getId(), row.type, row.price, row.shelfLifeHours, row.description, now()));
            }
            audit(data, "IMPORT_PRODUCTS", preview.getRows().size() + " product(s)");
            return preview.getRows().size();
        });
    }

    public int importIngredients(ImportPreview<IngredientRow> preview) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_INGREDIENTS);
        requireCommittable(preview);
        return store.execute("IMPORT_INGREDIENTS", data -> {
            for (IngredientRow row : preview.getRows()) {
                data.getIngredients().add(new Ingredient(data.nextId(EntityTypes.INGREDIENT), row.code, row.name,
                        row.unit, row.minimumStock, row.tracksExpiry, row.stockTracked, null, row.notes));
            }
            audit(data, "IMPORT_INGREDIENTS", preview.getRows().size() + " ingredient(s)");
            return preview.getRows().size();
        });
    }

    public int importSuppliers(ImportPreview<SupplierRow> preview) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_SUPPLIERS);
        requireCommittable(preview);
        return store.execute("IMPORT_SUPPLIERS", data -> {
            for (SupplierRow row : preview.getRows()) {
                data.getSuppliers().add(new Supplier(data.nextId(EntityTypes.SUPPLIER), row.name, row.contact,
                        row.phone, row.email, row.address, row.notes));
            }
            audit(data, "IMPORT_SUPPLIERS", preview.getRows().size() + " supplier(s)");
            return preview.getRows().size();
        });
    }

    private static void requireCommittable(ImportPreview<?> preview) throws ValidationException {
        if (!preview.canCommit()) {
            throw new ValidationException("The import has " + preview.getErrors().size()
                    + " error(s). Fix the file and run the check again.");
        }
    }

    private ProductCategory findOrCreateCategory(BakeryData data, String name) throws ValidationException {
        for (ProductCategory category : data.getCategories()) {
            if (TextUtil.sameKey(category.getName(), name)) {
                return category;
            }
        }
        ProductCategory created = new ProductCategory(data.nextId(EntityTypes.CATEGORY), name);
        data.getCategories().add(created);
        return created;
    }

    // ---- row mappers (one per entity type) ----

    /** Maps product CSV rows. Checks SKU format and uniqueness against the data and the file. */
    static class ProductRowMapper implements CsvRowMapper<ProductRow> {
        private final BakeryData data;
        private final Set<String> skusInFile = new HashSet<>();

        ProductRowMapper(BakeryData data) {
            this.data = data;
        }

        @Override
        public List<String> requiredColumns() {
            return Arrays.asList("name", "sku", "category", "type", "price", "shelf_life_hours");
        }

        @Override
        public ProductRow map(CsvReader.Row row, List<ImportError> errors) {
            int line = row.getLineNumber();
            int before = errors.size();
            ProductRow result = new ProductRow();
            result.name = TextUtil.normalize(row.get("name"));
            if (result.name.isEmpty() || result.name.length() > Product.MAX_NAME_LENGTH) {
                errors.add(new ImportError(line, "name", "Name must be 1-80 characters."));
            }
            result.sku = TextUtil.normalizeCode(row.get("sku"));
            if (!TextUtil.isValidSku(result.sku)) {
                errors.add(new ImportError(line, "sku", "Invalid SKU \"" + result.sku + "\"."));
            } else if (ProductService.skuTaken(data, result.sku, 0)) {
                errors.add(new ImportError(line, "sku", "SKU \"" + result.sku + "\" already exists."));
            } else if (!skusInFile.add(result.sku)) {
                errors.add(new ImportError(line, "sku", "SKU \"" + result.sku + "\" appears twice in the file."));
            }
            result.category = TextUtil.normalize(row.get("category"));
            if (result.category.isEmpty()) {
                errors.add(new ImportError(line, "category", "Category is required."));
            }
            try {
                result.type = ProductType.valueOf(row.get("type").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(new ImportError(line, "type", "Type must be MANUFACTURED or RESALE."));
            }
            try {
                result.price = NumberParser.parseMoney(row.get("price"), "price");
                if (result.price.signum() <= 0) {
                    errors.add(new ImportError(line, "price", "Price must be greater than zero."));
                }
            } catch (ValidationException e) {
                errors.add(new ImportError(line, "price", e.getMessage()));
            }
            try {
                result.shelfLifeHours = NumberParser.parseWholeNumber(row.get("shelf_life_hours"), "shelf_life_hours");
                if (result.shelfLifeHours < 1 || result.shelfLifeHours > Product.MAX_SHELF_LIFE_HOURS) {
                    errors.add(new ImportError(line, "shelf_life_hours", "Must be 1-8760 hours."));
                }
            } catch (ValidationException e) {
                errors.add(new ImportError(line, "shelf_life_hours", e.getMessage()));
            }
            result.description = row.get("description");
            return errors.size() == before ? result : null;
        }
    }

    static class IngredientRowMapper implements CsvRowMapper<IngredientRow> {
        private final BakeryData data;
        private final Set<String> codesInFile = new HashSet<>();

        IngredientRowMapper(BakeryData data) {
            this.data = data;
        }

        @Override
        public List<String> requiredColumns() {
            return Arrays.asList("code", "name", "unit", "minimum_stock");
        }

        @Override
        public IngredientRow map(CsvReader.Row row, List<ImportError> errors) {
            int line = row.getLineNumber();
            int before = errors.size();
            IngredientRow result = new IngredientRow();
            result.code = TextUtil.normalizeCode(row.get("code"));
            if (!TextUtil.CODE_PATTERN.matcher(result.code).matches()) {
                errors.add(new ImportError(line, "code", "Invalid code \"" + result.code + "\"."));
            } else if (codeTaken(result.code)) {
                errors.add(new ImportError(line, "code", "Code \"" + result.code + "\" already exists."));
            } else if (!codesInFile.add(result.code)) {
                errors.add(new ImportError(line, "code", "Code \"" + result.code + "\" appears twice in the file."));
            }
            result.name = TextUtil.normalize(row.get("name"));
            if (result.name.isEmpty()) {
                errors.add(new ImportError(line, "name", "Name is required."));
            }
            result.unit = parseBaseUnit(row.get("unit"));
            if (result.unit == null) {
                errors.add(new ImportError(line, "unit", "Unit must be G, ML or PCS."));
            }
            try {
                result.minimumStock = NumberParser.parseQuantity(row.get("minimum_stock"), "minimum_stock");
            } catch (ValidationException e) {
                errors.add(new ImportError(line, "minimum_stock", e.getMessage()));
            }
            result.tracksExpiry = parseYesNo(row.get("tracks_expiry"), true);
            result.stockTracked = parseYesNo(row.get("stock_tracked"), true);
            result.notes = row.get("notes");
            return errors.size() == before ? result : null;
        }

        private boolean codeTaken(String code) {
            for (Ingredient ingredient : data.getIngredients()) {
                if (ingredient.getCode().equalsIgnoreCase(code)) {
                    return true;
                }
            }
            return false;
        }
    }

    static class SupplierRowMapper implements CsvRowMapper<SupplierRow> {
        private final BakeryData data;
        private final Set<String> namesInFile = new HashSet<>();

        SupplierRowMapper(BakeryData data) {
            this.data = data;
        }

        @Override
        public List<String> requiredColumns() {
            return Arrays.asList("name");
        }

        @Override
        public SupplierRow map(CsvReader.Row row, List<ImportError> errors) {
            int line = row.getLineNumber();
            int before = errors.size();
            SupplierRow result = new SupplierRow();
            result.name = TextUtil.normalize(row.get("name"));
            if (result.name.isEmpty()) {
                errors.add(new ImportError(line, "name", "Name is required."));
            } else if (!namesInFile.add(result.name.toLowerCase(Locale.ROOT))) {
                errors.add(new ImportError(line, "name", "Supplier \"" + result.name + "\" appears twice in the file."));
            } else {
                for (Supplier existing : data.getSuppliers()) {
                    if (TextUtil.sameKey(existing.getName(), result.name)) {
                        errors.add(new ImportError(line, "name", "Supplier \"" + result.name + "\" already exists."));
                    }
                }
            }
            result.contact = row.get("contact_person");
            result.phone = row.get("phone");
            result.email = row.get("email");
            result.address = row.get("address");
            result.notes = row.get("notes");
            // reuse the entity's own validation (e-mail and phone format) without saving anything
            try {
                new Supplier(1, result.name.isEmpty() ? "x" : result.name, result.contact, result.phone, result.email,
                        result.address, result.notes);
            } catch (ValidationException e) {
                errors.add(new ImportError(line, "", e.getMessage()));
            }
            return errors.size() == before ? result : null;
        }
    }

    // ---- helpers ----

    static Unit parseBaseUnit(String text) {
        String value = text.trim().toUpperCase(Locale.ROOT);
        for (Unit unit : Unit.baseUnits()) {
            if (unit.name().equals(value) || unit.getSymbol().equalsIgnoreCase(value)) {
                return unit;
            }
        }
        return null;
    }

    static boolean parseYesNo(String text, boolean defaultValue) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return defaultValue;
        }
        return value.equals("yes") || value.equals("y") || value.equals("true") || value.equals("1") || value.equals("ya");
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String categoryName(long categoryId) {
        ProductCategory category = BakeryData.findById(data().getCategories(), categoryId);
        return category == null ? "" : category.getName();
    }

    private String itemName(StockKind kind, long itemId) {
        if (kind == StockKind.INGREDIENT) {
            Ingredient i = BakeryData.findById(data().getIngredients(), itemId);
            return i == null ? "?" : i.getName();
        }
        Product p = BakeryData.findById(data().getProducts(), itemId);
        return p == null ? "?" : p.getName();
    }

    private String lotCode(InventoryMovement m) {
        if (m.getStockKind() == StockKind.INGREDIENT) {
            app.panyaya.model.inventory.IngredientLot lot = BakeryData.findById(data().getIngredientLots(), m.getLotId());
            return lot == null ? "?" : lot.getLotCode();
        }
        app.panyaya.model.inventory.FinishedGoodsBatch batch =
                BakeryData.findById(data().getFinishedGoodsBatches(), m.getLotId());
        return batch == null ? "?" : batch.getLotCode();
    }
}
