package app.panyaya.model.production;

import app.panyaya.model.Unit;

import java.math.BigDecimal;

/**
 * Snapshot of one ingredient actually consumed by a production order: name, quantity and cost at
 * the moment production started. Later recipe or price edits do not change it.
 */
public class MaterialUsage {

    private long ingredientId;
    private String ingredientName;
    private BigDecimal quantity;
    private Unit unit;
    private BigDecimal cost;
    /** False for non-stock items such as tap water (listed but not consumed). */
    private boolean stockTracked;

    private MaterialUsage() {
    }

    public MaterialUsage(long ingredientId, String ingredientName, BigDecimal quantity, Unit unit, BigDecimal cost,
                         boolean stockTracked) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.quantity = quantity;
        this.unit = unit;
        this.cost = cost;
        this.stockTracked = stockTracked;
    }

    public long getIngredientId() {
        return ingredientId;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public boolean isStockTracked() {
        return stockTracked;
    }
}
