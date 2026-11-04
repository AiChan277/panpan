package app.panyaya.model.sales;

import java.math.BigDecimal;

/** Which finished-goods batch a sold quantity came from (FEFO), with that batch's cost. */
public class SaleAllocation {

    private long batchId;
    private String batchCode;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private long movementId;

    private SaleAllocation() {
    }

    public SaleAllocation(long batchId, String batchCode, BigDecimal quantity, BigDecimal unitCost, long movementId) {
        this.batchId = batchId;
        this.batchCode = batchCode;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.movementId = movementId;
    }

    public long getBatchId() {
        return batchId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public long getMovementId() {
        return movementId;
    }
}
