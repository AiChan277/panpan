package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.production.ProductionStatus;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Production orders planned in the period: planned vs good vs rejected, cost per piece. */
public class ProductionReport extends ReportGenerator {

    @Override
    public String getTitle() {
        return "Production";
    }

    @Override
    public String getDescription() {
        return "Planned versus good and rejected pieces per production order, with material cost and cost per piece.";
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Date", "Order", "Product", "Recipe", "Planned", "Good", "Rejected", "Status",
                "Material cost", "Cost / piece");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        List<ProductionOrder> orders = new ArrayList<>();
        for (ProductionOrder order : data.getProductionOrders()) {
            if (range.contains(order.getPlannedDate())) {
                orders.add(order);
            }
        }
        orders.sort(Comparator.comparing(ProductionOrder::getPlannedDate).thenComparing(ProductionOrder::getId));
        int planned = 0;
        int good = 0;
        int rejected = 0;
        int failed = 0;
        BigDecimal cost = Money.ZERO;
        report.setChartTitle("Good pieces by product");
        for (ProductionOrder order : orders) {
            report.addRow(Formatters.date(order.getPlannedDate()), order.getOrderNo(), order.getProductName(),
                    order.getRecipeName() + " v" + order.getRecipeVersion(), String.valueOf(order.getPlannedQuantity()),
                    String.valueOf(order.getGoodQuantity()), String.valueOf(order.getRejectedQuantity()),
                    order.getStatus().toString(), Formatters.money(order.getMaterialCost()),
                    order.getStatus() == ProductionStatus.COMPLETED ? Formatters.unitCost(order.getUnitCost()) : "-");
            if (order.getStatus() != ProductionStatus.CANCELLED) {
                planned += order.getPlannedQuantity();
            }
            good += order.getGoodQuantity();
            rejected += order.getRejectedQuantity();
            if (order.getStatus() == ProductionStatus.FAILED) {
                failed++;
            }
            cost = cost.add(order.getMaterialCost());
            if (order.getGoodQuantity() > 0) {
                report.addChartValue(order.getProductName(), BigDecimal.valueOf(order.getGoodQuantity()));
            }
        }
        report.addSummary(orders.size() + " order(s). Planned " + planned + ", good " + good + ", rejected " + rejected
                + ", failed batches " + failed + ".");
        report.addSummary("Material cost consumed: " + Formatters.money(cost));
    }
}
