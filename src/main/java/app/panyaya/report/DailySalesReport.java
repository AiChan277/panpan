package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.sales.Sale;
import app.panyaya.util.Formatters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Sales per day: transactions, items, revenue, cost of goods and gross profit. */
public class DailySalesReport extends ReportGenerator {

    @Override
    public String getTitle() {
        return "Daily sales";
    }

    @Override
    public String getDescription() {
        return "Revenue, cost of goods sold and gross profit per business day. Voided sales are counted separately.";
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Date", "Transactions", "Items sold", "Revenue", "Cost of goods", "Gross profit", "Voided");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        // TreeMap keeps the days sorted
        Map<LocalDate, SalesTotals> byDay = new TreeMap<>();
        for (LocalDate day : range.days()) {
            byDay.put(day, new SalesTotals());
        }
        SalesTotals total = new SalesTotals();
        for (Sale sale : data.getSales()) {
            if (range.contains(sale.getBusinessDate())) {
                byDay.get(sale.getBusinessDate()).add(sale);
                total.add(sale);
            }
        }
        report.setChartTitle("Revenue per day");
        for (Map.Entry<LocalDate, SalesTotals> entry : byDay.entrySet()) {
            SalesTotals day = entry.getValue();
            report.addRow(Formatters.date(entry.getKey()), String.valueOf(day.getTransactions()),
                    String.valueOf(day.getItems()), Formatters.money(day.getRevenue()), Formatters.money(day.getCost()),
                    Formatters.money(day.getGrossProfit()), String.valueOf(day.getVoided()));
            report.addChartValue(Formatters.DATE.format(entry.getKey()).substring(0, 6), day.getRevenue());
        }
        report.addSummary("Transactions: " + total.getTransactions() + "   Items: " + total.getItems()
                + "   Voided: " + total.getVoided());
        report.addSummary("Revenue: " + Formatters.money(total.getRevenue()) + "   Cost of goods: "
                + Formatters.money(total.getCost()) + "   Gross profit: " + Formatters.money(total.getGrossProfit())
                + " (" + Formatters.percent(total.getMarginPercent()) + ")");
        report.addSummary("Average transaction: " + Formatters.money(total.getAverageTransaction())
                + ". Gross profit excludes labour and overhead.");
    }
}
