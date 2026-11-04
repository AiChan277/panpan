package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.sales.Sale;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Sales per calendar month with month-over-month change. */
public class MonthlySalesReport extends ReportGenerator {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy");

    @Override
    public String getTitle() {
        return "Monthly sales";
    }

    @Override
    public String getDescription() {
        return "Revenue and gross profit per month in the selected range, with the change against the previous month.";
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Month", "Transactions", "Revenue", "Cost of goods", "Gross profit", "Margin", "vs. previous month");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        Map<YearMonth, SalesTotals> byMonth = new TreeMap<>();
        for (YearMonth m = YearMonth.from(range.getFrom()); !m.isAfter(YearMonth.from(range.getTo())); m = m.plusMonths(1)) {
            byMonth.put(m, new SalesTotals());
        }
        for (Sale sale : data.getSales()) {
            if (range.contains(sale.getBusinessDate())) {
                byMonth.get(YearMonth.from(sale.getBusinessDate())).add(sale);
            }
        }
        report.setChartTitle("Revenue per month");
        BigDecimal previous = null;
        for (Map.Entry<YearMonth, SalesTotals> entry : byMonth.entrySet()) {
            SalesTotals month = entry.getValue();
            String change = "-";
            if (previous != null && previous.signum() > 0) {
                BigDecimal percent = month.getRevenue().subtract(previous).multiply(BigDecimal.valueOf(100))
                        .divide(previous, 1, RoundingMode.HALF_UP);
                change = (percent.signum() > 0 ? "+" : "") + Formatters.percent(percent);
            }
            report.addRow(MONTH.format(entry.getKey()), String.valueOf(month.getTransactions()),
                    Formatters.money(month.getRevenue()), Formatters.money(month.getCost()),
                    Formatters.money(month.getGrossProfit()), Formatters.percent(month.getMarginPercent()), change);
            report.addChartValue(MONTH.format(entry.getKey()), month.getRevenue());
            previous = month.getRevenue();
        }
    }
}
