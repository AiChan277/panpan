package app.panyaya.service;

import app.panyaya.model.inventory.Ingredient;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Stock overview of one ingredient, for tables and the dashboard. */
public class IngredientStock {

    /** Traffic-light status. Shown with text and a symbol, never with colour alone. */
    public enum Status {
        OK("OK"),
        LOW("Low"),
        OUT("Out of stock"),
        NOT_TRACKED("Not tracked");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Ingredient ingredient;
    private final BigDecimal usableQuantity;
    private final BigDecimal expiredQuantity;
    private final BigDecimal value;
    private final LocalDate nearestExpiry;
    private final Status status;

    public IngredientStock(Ingredient ingredient, BigDecimal usableQuantity, BigDecimal expiredQuantity,
                           BigDecimal value, LocalDate nearestExpiry) {
        this.ingredient = ingredient;
        this.usableQuantity = usableQuantity;
        this.expiredQuantity = expiredQuantity;
        this.value = value;
        this.nearestExpiry = nearestExpiry;
        if (!ingredient.isStockTracked()) {
            this.status = Status.NOT_TRACKED;
        } else if (usableQuantity.signum() == 0) {
            this.status = Status.OUT;
        } else if (usableQuantity.compareTo(ingredient.getMinimumStock()) < 0) {
            this.status = Status.LOW;
        } else {
            this.status = Status.OK;
        }
    }

    public boolean needsAttention() {
        return status == Status.LOW || status == Status.OUT;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getUsableQuantity() {
        return usableQuantity;
    }

    public BigDecimal getExpiredQuantity() {
        return expiredQuantity;
    }

    public BigDecimal getValue() {
        return value;
    }

    public LocalDate getNearestExpiry() {
        return nearestExpiry;
    }

    public Status getStatus() {
        return status;
    }
}
