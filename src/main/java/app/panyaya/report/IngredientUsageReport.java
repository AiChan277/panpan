package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.inventory.ProductionConsumption;
import app.panyaya.model.inventory.StockAdjustment;
import app.panyaya.model.inventory.StockReceipt;
import app.panyaya.model.inventory.WasteWriteOff;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingredient movements in the period: received, used in production, wasted and adjusted.
 * Uses {@code instanceof} on the movement hierarchy to put each movement in the right column.
 */
public class IngredientUsageReport extends ReportGenerator {

    private static class Totals {
        BigDecimal received = Quantities.ZERO;
        BigDecimal used = Quantities.ZERO;
        BigDecimal wasted = Quantities.ZERO;
        BigDecimal adjusted = Quantities.ZERO;
        BigDecimal usedCost = Money.ZERO;
    }

    @Override
    public String getTitle() {
        return "Ingredient usage";
    }

    @Override
    public String getDescription() {
        return "Per ingredient: received, consumed by production, wasted and adjusted (net) in the period.";
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Ingredient", "Unit", "Received", "Used in production", "Cost of use", "Wasted", "Adjusted (net)");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        Map<Long, Totals> totals = new LinkedHashMap<>();
        for (Ingredient ingredient : data.getIngredients()) {
            totals.put(ingredient.getId(), new Totals());
        }
        for (InventoryMovement movement : data.getMovements()) {
            if (movement.getStockKind() != StockKind.INGREDIENT || !range.contains(movement.getBusinessDate())) {
                continue;
            }
            Totals row = totals.get(movement.getItemId());
            if (row == null) {
                continue;
            }
            if (movement instanceof StockReceipt) {
                row.received = row.received.add(movement.getQuantity());
            } else if (movement instanceof ProductionConsumption) {
                row.used = row.used.add(movement.getQuantity());
                row.usedCost = row.usedCost.add(Money.multiply(movement.getUnitCost(), movement.getQuantity()));
            } else if (movement instanceof WasteWriteOff) {
                row.wasted = row.wasted.add(movement.getQuantity());
            } else if (movement instanceof StockAdjustment) {
                row.adjusted = row.adjusted.add(movement.getSignedQuantity());
            }
        }
        BigDecimal totalCost = Money.ZERO;
        report.setChartTitle("Cost of ingredients used");
        for (Ingredient ingredient : data.getIngredients()) {
            Totals row = totals.get(ingredient.getId());
            boolean empty = row.received.signum() == 0 && row.used.signum() == 0 && row.wasted.signum() == 0
                    && row.adjusted.signum() == 0;
            if (empty) {
                continue;
            }
            report.addRow(ingredient.getName(), ingredient.getBaseUnit().getSymbol(), Formatters.quantity(row.received),
                    Formatters.quantity(row.used), Formatters.money(row.usedCost), Formatters.quantity(row.wasted),
                    Formatters.quantity(row.adjusted));
            totalCost = totalCost.add(row.usedCost);
            if (row.usedCost.signum() > 0) {
                report.addChartValue(ingredient.getName(), row.usedCost);
            }
        }
        report.addSummary("Total cost of ingredients used in production: " + Formatters.money(totalCost));
    }
}
