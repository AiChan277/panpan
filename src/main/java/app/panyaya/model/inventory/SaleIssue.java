package app.panyaya.model.inventory;

import app.panyaya.exception.BusinessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Finished goods leaving stock because they were sold at the POS. */
public class SaleIssue extends InventoryMovement {

    private long saleId;

    private SaleIssue() {
    }

    public SaleIssue(long id, Instant at, LocalDate businessDate, StockLot batch, BigDecimal quantity,
                     long userId, String reference, long saleId) {
        super(id, at, businessDate, batch, quantity, userId, reference, null);
        this.saleId = saleId;
    }

    @Override
    public boolean isIncoming() {
        return false;
    }

    @Override
    public String getTypeName() {
        return "Sale";
    }

    /** Customers are protected: an expired batch can never be sold. */
    @Override
    protected void checkAllowed(StockLot lot, Instant now, ZoneId zone) throws BusinessException {
        if (lot.isExpiredAt(now, zone)) {
            throw new BusinessException("Batch " + lot.getLotCode() + " is expired and cannot be sold.");
        }
    }

    public long getSaleId() {
        return saleId;
    }
}
