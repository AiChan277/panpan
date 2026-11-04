package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.product.Product;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleLine;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per product: quantity sold, revenue, cost, gross profit, margin and waste. */
public class ProductPerformanceReport extends ReportGenerator {

    /** Running totals for one product. */
    private static class Row {
        String name;
        String sku;
        int quantity;
        BigDecimal revenue = Money.ZERO;
        BigDecimal cost = Money.ZERO;
        BigDecimal wasteQuantity = Quantities.ZERO;
        BigDecimal wasteCost = Money.ZERO;
    }

    @Override
    public String getTitle() {
        return "Product performance";
    }

    @Override
    public String getDescription() {
        return "Which products earn money and which are wasted. Sorted by revenue.";
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Product", "SKU", "Qty sold", "Revenue", "Cost of goods", "Gross profit", "Margin",
                "Waste qty", "Waste cost");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        Map<Long, Row> rows = new HashMap<>();
        for (Sale sale : data.getSales()) {
            if (!sale.isCompleted() || !range.contains(sale.getBusinessDate())) {
                continue;
            }
            for (SaleLine line : sale.getLines()) {
                Row row = rowFor(rows, data, line.getProductId());
                row.quantity += line.getQuantity();
                row.revenue = row.revenue.add(line.getLineTotal());
                row.cost = row.cost.add(line.getCostTotal());
            }
        }
        for (WasteRecord waste : data.getWasteRecords()) {
            if (waste.getStockKind() == StockKind.PRODUCT && range.contains(waste.getBusinessDate())) {
                Row row = rowFor(rows, data, waste.getItemId());
                row.wasteQuantity = row.wasteQuantity.add(waste.getQuantity());
                row.wasteCost = row.wasteCost.add(waste.getEstimatedLoss());
            }
        }
        List<Row> sorted = new ArrayList<>(rows.values());
        sorted.sort((a, b) -> b.revenue.compareTo(a.revenue));
        report.setChartTitle("Revenue by product");
        for (Row row : sorted) {
            BigDecimal profit = row.revenue.subtract(row.cost);
            String margin = row.revenue.signum() == 0 ? "-" : Formatters.percent(
                    profit.multiply(BigDecimal.valueOf(100)).divide(row.revenue, 1, RoundingMode.HALF_UP));
            report.addRow(row.name, row.sku, String.valueOf(row.quantity), Formatters.money(row.revenue),
                    Formatters.money(row.cost), Formatters.money(profit), margin,
                    Formatters.quantity(row.wasteQuantity), Formatters.money(row.wasteCost));
            if (row.revenue.signum() > 0) {
                report.addChartValue(row.name, row.revenue);
            }
        }
        report.addSummary(sorted.size() + " product(s) with sales or waste in the period.");
    }

    private static Row rowFor(Map<Long, Row> rows, BakeryData data, long productId) {
        Row row = rows.get(productId);
        if (row == null) {
            row = new Row();
            Product product = BakeryData.findById(data.getProducts(), productId);
            row.name = product == null ? "#" + productId : product.getName();
            row.sku = product == null ? "" : product.getSku();
            rows.put(productId, row);
        }
        return row;
    }
}
