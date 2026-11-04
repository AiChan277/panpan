package app.panyaya.model.inventory;

import app.panyaya.exception.BusinessException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * One line of the append-only stock ledger. Every change of a lot's quantity is one movement.
 *
 * <p>Course topics:
 * <ul>
 *   <li><b>Inheritance</b>: seven concrete movement types extend this class and share the id,
 *       time, quantity, cost and reference fields.</li>
 *   <li><b>Polymorphism</b>: {@link #applyTo} is the same call for every movement, but the result
 *       depends on the concrete object: {@link #isIncoming()} decides whether stock goes up or down
 *       and {@link #checkAllowed} adds type-specific rules (production may not use an expired lot,
 *       a sale may not sell an expired batch, waste may write off expired stock).</li>
 *   <li><b>Abstraction</b>: the class is abstract; there is no "plain" movement.</li>
 * </ul>
 */
public abstract class InventoryMovement extends BaseEntity {

    private Instant occurredAt;
    private LocalDate businessDate;
    private StockKind stockKind;
    private long itemId;
    private long lotId;
    /** Always positive; the direction comes from the concrete type. */
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private String reference;
    private long userId;
    private String note;

    protected InventoryMovement() {
    }

    protected InventoryMovement(long id, Instant occurredAt, LocalDate businessDate, StockLot lot,
                                BigDecimal quantity, long userId, String reference, String note) {
        super(id);
        if (!Quantities.isPositive(quantity)) {
            throw new IllegalArgumentException("Movement quantity must be positive");
        }
        this.occurredAt = occurredAt;
        this.businessDate = businessDate;
        this.stockKind = lot.getStockKind();
        this.itemId = lot.getItemId();
        this.lotId = lot.getId();
        this.quantity = Quantities.normalize(quantity);
        this.unitCost = lot.getUnitCost();
        this.userId = userId;
        this.reference = reference;
        this.note = note;
    }

    /** True if this movement adds stock, false if it removes stock. */
    public abstract boolean isIncoming();

    /** Short label for the ledger screen, e.g. "Sale". */
    public abstract String getTypeName();

    /**
     * Hook for type-specific rules, called before the quantity changes. The default allows
     * everything; subclasses override it. (Template Method: {@link #applyTo} fixes the steps.)
     */
    protected void checkAllowed(StockLot lot, Instant now, ZoneId zone) throws BusinessException {
        // default: no extra rule
    }

    /**
     * Applies this movement to its lot: checks the rules, then increases or decreases the
     * remaining quantity. This is the <b>only</b> way a lot's quantity can change.
     */
    public final void applyTo(StockLot lot, Instant now, ZoneId zone) throws BusinessException {
        if (lot.getId() != lotId || lot.getStockKind() != stockKind) {
            throw new IllegalArgumentException("Movement " + getId() + " does not belong to lot " + lot.getId());
        }
        checkAllowed(lot, now, zone);
        if (isIncoming()) {
            lot.increase(quantity);
        } else {
            lot.decrease(quantity);
        }
    }

    /** +quantity for incoming movements, -quantity for outgoing ones. */
    public BigDecimal getSignedQuantity() {
        return isIncoming() ? quantity : quantity.negate();
    }

    /** Value of this movement at the lot's unit cost (signed). */
    public BigDecimal getSignedValue() {
        return Money.multiply(unitCost, getSignedQuantity());
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public StockKind getStockKind() {
        return stockKind;
    }

    public long getItemId() {
        return itemId;
    }

    public long getLotId() {
        return lotId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getReference() {
        return reference;
    }

    public long getUserId() {
        return userId;
    }

    public String getNote() {
        return note;
    }
}
