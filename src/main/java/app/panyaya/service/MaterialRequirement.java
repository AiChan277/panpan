package app.panyaya.service;

import app.panyaya.model.Quantities;
import app.panyaya.model.Unit;

import java.math.BigDecimal;

/** One ingredient line of a requirements calculation: needed vs. available. */
public class MaterialRequirement {

    private final long ingredientId;
    private final String ingredientName;
    private final Unit unit;
    private final BigDecimal required;
    private final BigDecimal available;
    private final boolean stockTracked;
    /** Estimated cost of the required quantity; null when the ingredient's cost is unknown. */
    private final BigDecimal estimatedCost;

    public MaterialRequirement(long ingredientId, String ingredientName, Unit unit, BigDecimal required,
                               BigDecimal available, boolean stockTracked, BigDecimal estimatedCost) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.unit = unit;
        this.required = required;
        this.available = available;
        this.stockTracked = stockTracked;
        this.estimatedCost = estimatedCost;
    }

    /** How much is missing (zero when enough is available or the item is not stock-tracked). */
    public BigDecimal getShortfall() {
        if (!stockTracked || available.compareTo(required) >= 0) {
            return Quantities.ZERO;
        }
        return Quantities.normalize(required.subtract(available));
    }

    public boolean isShort() {
        return getShortfall().signum() > 0;
    }

    public long getIngredientId() {
        return ingredientId;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public Unit getUnit() {
        return unit;
    }

    public BigDecimal getRequired() {
        return required;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public boolean isStockTracked() {
        return stockTracked;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }
}
