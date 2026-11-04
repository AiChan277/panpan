package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.product.Product;
import app.panyaya.service.StockQueries;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** Current finished-goods stock per product (date range not used). */
public class FinishedGoodsReport extends ReportGenerator {

    @Override
    public String getTitle() {
        return "Finished goods stock";
    }

    @Override
    public String getDescription() {
        return "Sellable pieces per product, expired pieces that still need to be written off, and stock value.";
    }

    @Override
    public boolean usesDateRange() {
        return false;
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Product", "SKU", "Sellable", "Expired (write off)", "Value (sellable)", "Value at risk",
                "Next expiry");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        BigDecimal totalValue = Money.ZERO;
        BigDecimal totalRisk = Money.ZERO;
        report.setChartTitle("Sellable pieces by product");
        for (Product product : data.getProducts()) {
            BigDecimal sellable = Quantities.ZERO;
            BigDecimal expired = Quantities.ZERO;
            BigDecimal value = Money.ZERO;
            BigDecimal risk = Money.ZERO;
            Instant nextExpiry = null;
            for (FinishedGoodsBatch batch : StockQueries.batchesOf(data, product.getId())) {
                if (batch.isDepleted()) {
                    continue;
                }
                if (batch.isExpiredAt(now, zone)) {
                    expired = expired.add(batch.getRemainingQuantity());
                    risk = risk.add(batch.getRemainingValue());
                } else {
                    sellable = sellable.add(batch.getRemainingQuantity());
                    value = value.add(batch.getRemainingValue());
                    if (nextExpiry == null || batch.getExpiresAt().isBefore(nextExpiry)) {
                        nextExpiry = batch.getExpiresAt();
                    }
                }
            }
            if (!product.isActive() && sellable.signum() == 0 && expired.signum() == 0) {
                continue;
            }
            report.addRow(product.getName(), product.getSku(), Formatters.quantity(sellable), Formatters.quantity(expired),
                    Formatters.money(value), Formatters.money(risk), Formatters.dateTime(nextExpiry, zone));
            totalValue = totalValue.add(value);
            totalRisk = totalRisk.add(risk);
            if (sellable.signum() > 0) {
                report.addChartValue(product.getName(), sellable);
            }
        }
        report.addSummary("Sellable stock value: " + Formatters.money(totalValue)
                + "   Expired stock value (at risk): " + Formatters.money(totalRisk));
    }
}
