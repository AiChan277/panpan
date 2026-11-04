package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Totals per payment method - useful for cash closing. */
public class PaymentMethodReport extends ReportGenerator {

    @Override
    public String getTitle() {
        return "Sales by payment method";
    }

    @Override
    public String getDescription() {
        return "How much was paid in cash, QRIS, card and transfer. Compare the cash line with the cash drawer.";
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Payment method", "Transactions", "Amount", "Share");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        // EnumMap: a Map specialised for enum keys, iterates in declaration order
        Map<PaymentMethod, Integer> counts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> amounts = new EnumMap<>(PaymentMethod.class);
        BigDecimal total = Money.ZERO;
        for (Sale sale : data.getSales()) {
            if (sale.isCompleted() && range.contains(sale.getBusinessDate())) {
                counts.merge(sale.getPaymentMethod(), 1, Integer::sum);
                amounts.merge(sale.getPaymentMethod(), sale.getTotal(), BigDecimal::add);
                total = total.add(sale.getTotal());
            }
        }
        report.setChartTitle("Amount by payment method");
        for (PaymentMethod method : PaymentMethod.values()) {
            BigDecimal amount = amounts.getOrDefault(method, Money.ZERO);
            String share = total.signum() == 0 ? "-" : Formatters.percent(
                    amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP));
            report.addRow(method.toString(), String.valueOf(counts.getOrDefault(method, 0)), Formatters.money(amount), share);
            if (amount.signum() > 0) {
                report.addChartValue(method.toString(), amount);
            }
        }
        report.addSummary("Total: " + Formatters.money(total));
    }
}
