package app.panyaya.model.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Manual correction by the Owner after a physical count. Unlike the other movement types, its
 * direction is decided per object (the {@code increase} field), which shows that polymorphic
 * behaviour can depend on both the class and the object's own state.
 */
public class StockAdjustment extends InventoryMovement {

    private boolean increase;
    private AdjustmentReason reason;

    private StockAdjustment() {
    }

    public StockAdjustment(long id, Instant at, LocalDate businessDate, StockLot lot, BigDecimal quantity,
                           long userId, String reference, boolean increase, AdjustmentReason reason, String note) {
        super(id, at, businessDate, lot, quantity, userId, reference, note);
        this.increase = increase;
        this.reason = reason;
    }

    @Override
    public boolean isIncoming() {
        return increase;
    }

    @Override
    public String getTypeName() {
        return increase ? "Adjustment (+)" : "Adjustment (-)";
    }

    public AdjustmentReason getReason() {
        return reason;
    }
}
