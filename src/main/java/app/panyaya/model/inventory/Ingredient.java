package app.panyaya.model.inventory;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Quantities;
import app.panyaya.model.Unit;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;

/**
 * Raw material master data (flour, butter, eggs...). The quantity on hand is <i>not</i> a field
 * here: it is the sum of the remaining quantities of the ingredient's {@link IngredientLot}s, so it
 * can never drift away from the lots and the movement ledger.
 */
public class Ingredient extends BaseEntity {

    private String code;
    private String name;
    private Unit baseUnit;
    private BigDecimal minimumStock;
    private boolean tracksExpiry;
    /** False for things like tap water that appear in recipes but are not counted in stock. */
    private boolean stockTracked = true;
    private Long defaultSupplierId;
    private String notes;
    private boolean active = true;

    private Ingredient() {
    }

    public Ingredient(long id, String code, String name, Unit baseUnit, BigDecimal minimumStock,
                      boolean tracksExpiry, boolean stockTracked, Long defaultSupplierId, String notes)
            throws ValidationException {
        super(id);
        if (baseUnit == null || !baseUnit.isBaseUnit()) {
            throw new ValidationException("Stock unit must be a base unit: g, mL or pcs.");
        }
        this.baseUnit = baseUnit;
        update(code, name, minimumStock, tracksExpiry, stockTracked, defaultSupplierId, notes);
    }

    public void update(String newCode, String newName, BigDecimal newMinimum, boolean newTracksExpiry,
                       boolean newStockTracked, Long newDefaultSupplierId, String newNotes)
            throws ValidationException {
        String cleanCode = TextUtil.normalizeCode(newCode);
        if (!TextUtil.CODE_PATTERN.matcher(cleanCode).matches()) {
            throw new ValidationException("Ingredient code \"" + cleanCode + "\" is invalid. Use 2-20 letters, digits or '-'.");
        }
        String cleanName = TextUtil.normalize(newName);
        if (cleanName.isEmpty() || cleanName.length() > 80) {
            throw new ValidationException("Ingredient name must be 1-80 characters.");
        }
        BigDecimal minimum = Quantities.normalize(newMinimum);
        if (minimum.signum() < 0) {
            throw new ValidationException("Minimum stock cannot be negative.");
        }
        this.code = cleanCode;
        this.name = cleanName;
        this.minimumStock = minimum;
        this.tracksExpiry = newTracksExpiry;
        this.stockTracked = newStockTracked;
        this.defaultSupplierId = newDefaultSupplierId;
        this.notes = TextUtil.normalize(newNotes);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Unit getBaseUnit() {
        return baseUnit;
    }

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public boolean isTracksExpiry() {
        return tracksExpiry;
    }

    public boolean isStockTracked() {
        return stockTracked;
    }

    public Long getDefaultSupplierId() {
        return defaultSupplierId;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return name + " (" + baseUnit.getSymbol() + ")";
    }
}
