package app.panyaya.model.supplier;

import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One received item of a goods receipt. Creates exactly one lot or batch. */
public class GoodsReceiptLine {

    private StockKind stockKind;
    private long itemId;
    private String itemName;
    private BigDecimal quantity;
    private Unit unit;
    private BigDecimal baseQuantity;
    private BigDecimal totalCost;
    private BigDecimal unitCost;
    private String lotCode;
    private LocalDate expiryDate;
    private long lotId;

    private GoodsReceiptLine() {
    }

    public GoodsReceiptLine(StockKind stockKind, long itemId, String itemName, BigDecimal quantity, Unit unit,
                            BigDecimal baseQuantity, BigDecimal totalCost, BigDecimal unitCost, String lotCode,
                            LocalDate expiryDate, long lotId) {
        this.stockKind = stockKind;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unit = unit;
        this.baseQuantity = baseQuantity;
        this.totalCost = totalCost;
        this.unitCost = unitCost;
        this.lotCode = lotCode;
        this.expiryDate = expiryDate;
        this.lotId = lotId;
    }

    public StockKind getStockKind() {
        return stockKind;
    }

    public long getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getLotCode() {
        return lotCode;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public long getLotId() {
        return lotId;
    }
}
