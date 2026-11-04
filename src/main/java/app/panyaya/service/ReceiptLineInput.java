package app.panyaya.service;

import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line typed into the "Receive goods" dialog, before it is validated and saved. */
public class ReceiptLineInput {

    private final StockKind stockKind;
    private final long itemId;
    private final BigDecimal quantity;
    private final Unit unit;
    private final BigDecimal totalCost;
    private final String lotCode;
    private final LocalDate expiryDate;

    public ReceiptLineInput(StockKind stockKind, long itemId, BigDecimal quantity, Unit unit, BigDecimal totalCost,
                            String lotCode, LocalDate expiryDate) {
        this.stockKind = stockKind;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unit = unit;
        this.totalCost = totalCost;
        this.lotCode = lotCode;
        this.expiryDate = expiryDate;
    }

    public StockKind getStockKind() {
        return stockKind;
    }

    public long getItemId() {
        return itemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public String getLotCode() {
        return lotCode;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
