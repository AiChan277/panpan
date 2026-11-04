package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.service.IngredientService;
import app.panyaya.service.IngredientStock;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** Current raw-material stock, status and value (date range not used). */
public class IngredientInventoryReport extends ReportGenerator {

    @Override
    public String getTitle() {
        return "Ingredient inventory";
    }

    @Override
    public String getDescription() {
        return "Current usable stock of every ingredient with status, value at lot cost, expired quantity and nearest expiry.";
    }

    @Override
    public boolean usesDateRange() {
        return false;
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Ingredient", "Code", "Usable", "Unit", "Minimum", "Status", "Value", "Expired qty",
                "Nearest expiry");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        BigDecimal totalValue = Money.ZERO;
        int attention = 0;
        report.setChartTitle("Stock value by ingredient");
        for (Ingredient ingredient : data.getIngredients()) {
            if (!ingredient.isActive()) {
                continue;
            }
            IngredientStock stock = IngredientService.stockOf(data, ingredient, now, zone);
            report.addRow(ingredient.getName(), ingredient.getCode(), Formatters.quantity(stock.getUsableQuantity()),
                    ingredient.getBaseUnit().getSymbol(), Formatters.quantity(ingredient.getMinimumStock()),
                    stock.getStatus().toString(), Formatters.money(stock.getValue()),
                    Formatters.quantity(stock.getExpiredQuantity()), Formatters.date(stock.getNearestExpiry()));
            totalValue = totalValue.add(stock.getValue());
            if (stock.needsAttention()) {
                attention++;
            }
            if (stock.getValue().signum() > 0) {
                report.addChartValue(ingredient.getName(), stock.getValue());
            }
        }
        report.addSummary("Total ingredient stock value: " + Formatters.money(totalValue));
        report.addSummary(attention + " ingredient(s) low or out of stock.");
    }
}
