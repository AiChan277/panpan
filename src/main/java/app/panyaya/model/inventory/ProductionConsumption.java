package app.panyaya.model.inventory;

import app.panyaya.exception.BusinessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Ingredients used when a production batch is started. */
public class ProductionConsumption extends InventoryMovement {

    private long productionOrderId;

    private ProductionConsumption() {
    }

    public ProductionConsumption(long id, Instant at, LocalDate businessDate, StockLot lot, BigDecimal quantity,
                                 long userId, String reference, long productionOrderId) {
        super(id, at, businessDate, lot, quantity, userId, reference, null);
        this.productionOrderId = productionOrderId;
    }

    @Override
    public boolean isIncoming() {
        return false;
    }

    @Override
    public String getTypeName() {
        return "Production use";
    }

    /** Expired ingredients must never go into food. */
    @Override
    protected void checkAllowed(StockLot lot, Instant now, ZoneId zone) throws BusinessException {
        if (lot.isExpiredAt(now, zone)) {
            throw new BusinessException("Lot " + lot.getLotCode() + " is expired and cannot be used for production.");
        }
    }

    public long getProductionOrderId() {
        return productionOrderId;
    }
}
