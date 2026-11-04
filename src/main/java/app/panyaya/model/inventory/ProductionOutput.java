package app.panyaya.model.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Good products added to finished-goods stock when a production order is completed. */
public class ProductionOutput extends InventoryMovement {

    private long productionOrderId;

    private ProductionOutput() {
    }

    public ProductionOutput(long id, Instant at, LocalDate businessDate, StockLot batch, BigDecimal quantity,
                            long userId, String reference, long productionOrderId) {
        super(id, at, businessDate, batch, quantity, userId, reference, null);
        this.productionOrderId = productionOrderId;
    }

    @Override
    public boolean isIncoming() {
        return true;
    }

    @Override
    public String getTypeName() {
        return "Production output";
    }

    public long getProductionOrderId() {
        return productionOrderId;
    }
}
