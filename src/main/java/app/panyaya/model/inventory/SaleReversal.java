package app.panyaya.model.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Compensating movement created when a sale is voided: the goods go back to the batch they were
 * taken from. The original {@link SaleIssue} is never edited or deleted.
 */
public class SaleReversal extends InventoryMovement {

    private long saleId;
    private long reversedMovementId;

    private SaleReversal() {
    }

    public SaleReversal(long id, Instant at, LocalDate businessDate, StockLot batch, BigDecimal quantity,
                        long userId, String reference, long saleId, long reversedMovementId, String reason) {
        super(id, at, businessDate, batch, quantity, userId, reference, reason);
        this.saleId = saleId;
        this.reversedMovementId = reversedMovementId;
    }

    @Override
    public boolean isIncoming() {
        return true;
    }

    @Override
    public String getTypeName() {
        return "Sale void";
    }

    public long getSaleId() {
        return saleId;
    }

    public long getReversedMovementId() {
        return reversedMovementId;
    }
}
