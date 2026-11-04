package app.panyaya.model.product;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A sellable bakery product, e.g. "Japanese Milk Bread". Products are data created from the UI,
 * never Java subclasses per bread type.
 *
 * <p>Course topic <b>Encapsulation</b>: all fields are private. The selling price can only change
 * through {@link #changePrice}, which validates the value and records a {@link PriceChange} row.
 * Sales copy the price at checkout, so history is not affected by later price changes.</p>
 */
public class Product extends BaseEntity {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_SHELF_LIFE_HOURS = 8760;

    private String name;
    private String sku;
    private long categoryId;
    private ProductType type;
    private BigDecimal sellingPrice;
    private int shelfLifeHours;
    private String description;
    private boolean active = true;
    private Instant createdAt;
    private List<PriceChange> priceHistory = new ArrayList<>();

    private Product() {
    }

    public Product(long id, String name, String sku, long categoryId, ProductType type,
                   BigDecimal sellingPrice, int shelfLifeHours, String description, Instant createdAt)
            throws ValidationException {
        super(id);
        if (type == null) {
            throw new ValidationException("Product type is required.");
        }
        this.type = type;
        this.createdAt = createdAt;
        updateDetails(name, sku, categoryId, shelfLifeHours, description);
        this.sellingPrice = validatePrice(sellingPrice);
    }

    /** Updates the descriptive fields. Uniqueness of the SKU is checked by ProductService. */
    public void updateDetails(String name, String sku, long categoryId, int shelfLifeHours, String description)
            throws ValidationException {
        String cleanName = TextUtil.normalize(name);
        if (cleanName.isEmpty() || cleanName.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Product name must be 1-" + MAX_NAME_LENGTH + " characters.");
        }
        String cleanSku = TextUtil.normalizeCode(sku);
        if (!TextUtil.isValidSku(cleanSku)) {
            throw new ValidationException("SKU \"" + cleanSku + "\" is invalid. Use 2-20 letters, digits or '-'.");
        }
        if (categoryId <= 0) {
            throw new ValidationException("Please choose a category.");
        }
        if (shelfLifeHours < 1 || shelfLifeHours > MAX_SHELF_LIFE_HOURS) {
            throw new ValidationException("Shelf life must be between 1 and " + MAX_SHELF_LIFE_HOURS + " hours.");
        }
        this.name = cleanName;
        this.sku = cleanSku;
        this.categoryId = categoryId;
        this.shelfLifeHours = shelfLifeHours;
        this.description = TextUtil.normalize(description);
    }

    /** Changes the selling price and records the change in the price history. */
    public void changePrice(BigDecimal newPrice, long userId, Instant at) throws ValidationException {
        BigDecimal value = validatePrice(newPrice);
        if (value.compareTo(sellingPrice) == 0) {
            return; // nothing changed, no history row
        }
        priceHistory.add(new PriceChange(sellingPrice, value, at, userId));
        this.sellingPrice = value;
    }

    /** Type may only change while the product has no stock and no recipe (checked by the service). */
    public void changeType(ProductType newType) throws ValidationException {
        if (newType == null) {
            throw new ValidationException("Product type is required.");
        }
        this.type = newType;
    }

    private static BigDecimal validatePrice(BigDecimal price) throws ValidationException {
        if (!Money.isPositive(price)) {
            throw new ValidationException("Selling price must be greater than zero.");
        }
        if (price.compareTo(Money.MAX_PRICE) > 0) {
            throw new ValidationException("Selling price is unrealistically high (max Rp 100.000.000).");
        }
        return Money.normalize(price);
    }

    /** Archived products disappear from POS and planning but stay in history and reports. */
    public void archive() {
        this.active = false;
    }

    public void restore() {
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public ProductType getType() {
        return type;
    }

    public boolean isManufactured() {
        return type == ProductType.MANUFACTURED;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public int getShelfLifeHours() {
        return shelfLifeHours;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Read-only view: callers cannot add or remove history rows. */
    public List<PriceChange> getPriceHistory() {
        return Collections.unmodifiableList(priceHistory);
    }

    @Override
    public String toString() {
        return name + " (" + sku + ")";
    }
}
