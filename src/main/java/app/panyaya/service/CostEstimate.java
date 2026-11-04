package app.panyaya.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Estimated material cost of a recipe. When some ingredient has never been received, its cost is
 * unknown: the estimate is then marked incomplete instead of pretending the cost is zero.
 */
public class CostEstimate {

    private final BigDecimal batchCost;
    private final BigDecimal costPerUnit;
    private final List<String> unknownCostIngredients;

    public CostEstimate(BigDecimal batchCost, BigDecimal costPerUnit, List<String> unknownCostIngredients) {
        this.batchCost = batchCost;
        this.costPerUnit = costPerUnit;
        this.unknownCostIngredients = Collections.unmodifiableList(unknownCostIngredients);
    }

    public boolean isComplete() {
        return unknownCostIngredients.isEmpty();
    }

    public BigDecimal getBatchCost() {
        return batchCost;
    }

    public BigDecimal getCostPerUnit() {
        return costPerUnit;
    }

    public List<String> getUnknownCostIngredients() {
        return unknownCostIngredients;
    }
}
