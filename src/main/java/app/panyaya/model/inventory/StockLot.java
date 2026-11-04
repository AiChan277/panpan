package app.panyaya.model.inventory;

import app.panyaya.exception.InsufficientStockException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A quantity of one item that arrived together and shares one cost and one expiry:
 * an ingredient lot (25 kg of flour from one delivery) or a finished-goods batch (45 buns from
 * one bake).
 *
 * <p>Course topics:
 * <ul>
 *   <li><b>Abstraction</b>: this class is abstract; "expired" means something different for
 *       each subclass, so {@link #isExpiredAt} is abstract.</li>
 *   <li><b>Inheritance</b>: {@link IngredientLot} and {@link FinishedGoodsBatch} reuse all the
 *       quantity and cost handling below.</li>
 *   <li><b>Encapsulation</b>: {@link #increase} and {@link #decrease} are package-private, so only
 *       {@link InventoryMovement} objects (same package) can change a quantity. Every change is
 *       therefore recorded in the movement ledger. Stock can never become negative.</li>
 * </ul>
 */
public abstract class StockLot extends BaseEntity {

    private long itemId;
    private String lotCode;
    private BigDecimal initialQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal unitCost;
    private Instant receivedAt;

    protected StockLot() {
    }

    protected StockLot(long id, long itemId, String lotCode, BigDecimal initialQuantity, BigDecimal unitCost,
                       Instant receivedAt) {
        super(id);
        if (!Quantities.isPositive(initialQuantity)) {
            throw new IllegalArgumentException("Lot quantity must be positive");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost cannot be negative");
        }
        this.itemId = itemId;
        this.lotCode = lotCode;
        this.initialQuantity = Quantities.normalize(initialQuantity);
        // A new lot starts EMPTY: the first StockReceipt / ProductionOutput movement fills it,
        // so the ledger always explains the full quantity.
        this.remainingQuantity = Quantities.ZERO;
        this.unitCost = Money.normalize(unitCost);
        this.receivedAt = receivedAt;
    }

    /** Whether this lot holds an ingredient or a finished product. */
    public abstract StockKind getStockKind();

    /** Each subclass decides what "expired" means (date for ingredients, exact time for bread). */
    public abstract boolean isExpiredAt(Instant now, ZoneId zone);

    /** Sort key for FEFO (first-expired-first-out). {@code null} means "never expires". */
    public abstract Instant getExpirySortKey(ZoneId zone);

    /** Human-readable expiry, e.g. "12 Nov 2026". */
    public abstract String describeExpiry(ZoneId zone);

    /** A lot can be used (sold, consumed) when it still has stock and is not expired. */
    public boolean isUsableAt(Instant now, ZoneId zone) {
        return remainingQuantity.signum() > 0 && !isExpiredAt(now, zone);
    }

    /** Package-private: only movements may call this. */
    void increase(BigDecimal quantity) {
        if (!Quantities.isPositive(quantity)) {
            throw new IllegalArgumentException("Increase must be positive");
        }
        remainingQuantity = Quantities.normalize(remainingQuantity.add(quantity));
    }

    /** Package-private: only movements may call this. Refuses to go below zero. */
    void decrease(BigDecimal quantity) throws InsufficientStockException {
        if (!Quantities.isPositive(quantity)) {
            throw new IllegalArgumentException("Decrease must be positive");
        }
        if (quantity.compareTo(remainingQuantity) > 0) {
            throw new InsufficientStockException("Lot " + lotCode + " has only "
                    + Formatters.quantity(remainingQuantity) + " left, cannot take " + Formatters.quantity(quantity));
        }
        remainingQuantity = Quantities.normalize(remainingQuantity.subtract(quantity));
        assert remainingQuantity.signum() >= 0 : "stock must never become negative";
    }

    public boolean isDepleted() {
        return remainingQuantity.signum() == 0;
    }

    /** Value of the remaining stock at this lot's cost. */
    public BigDecimal getRemainingValue() {
        return Money.multiply(unitCost, remainingQuantity);
    }

    public long getItemId() {
        return itemId;
    }

    public String getLotCode() {
        return lotCode;
    }

    public BigDecimal getInitialQuantity() {
        return initialQuantity;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
