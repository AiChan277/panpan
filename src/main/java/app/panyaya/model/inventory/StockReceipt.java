package app.panyaya.model.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Goods received from a supplier (or opening stock). Fills a new lot or batch. */
public class StockReceipt extends InventoryMovement {

    /** Goods receipt id; 0 for opening stock entered together with a new ingredient. */
    private long receiptId;

    private StockReceipt() {
    }

    public StockReceipt(long id, Instant at, LocalDate businessDate, StockLot lot, BigDecimal quantity,
                        long userId, String reference, long receiptId) {
        super(id, at, businessDate, lot, quantity, userId, reference, null);
        this.receiptId = receiptId;
    }

    @Override
    public boolean isIncoming() {
        return true;
    }

    @Override
    public String getTypeName() {
        return "Stock receipt";
    }

    public long getReceiptId() {
        return receiptId;
    }
}
