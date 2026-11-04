package app.panyaya.report;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.util.Formatters;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Every waste record in the period, with totals per reason. */
public class WasteReport extends ReportGenerator {

    @Override
    public String getTitle() {
        return "Waste";
    }

    @Override
    public String getDescription() {
        return "What was thrown away, why, and what it cost (quantity x lot cost).";
    }

    @Override
    protected List<String> getColumns() {
        return Arrays.asList("Date", "Waste no.", "Kind", "Item", "Lot / batch", "Quantity", "Reason", "Estimated loss", "Note");
    }

    @Override
    protected void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone) {
        List<WasteRecord> records = new ArrayList<>();
        for (WasteRecord record : data.getWasteRecords()) {
            if (range.contains(record.getBusinessDate())) {
                records.add(record);
            }
        }
        records.sort(Comparator.comparing(WasteRecord::getOccurredAt));
        Map<WasteReason, BigDecimal> byReason = new EnumMap<>(WasteReason.class);
        BigDecimal total = Money.ZERO;
        for (WasteRecord record : records) {
            report.addRow(Formatters.date(record.getBusinessDate()), record.getWasteNo(), record.getStockKind().toString(),
                    record.getItemName(), TextUtil.orDash(record.getLotCode()),
                    Formatters.quantity(record.getQuantity(), record.getUnit()), record.getReason().toString(),
                    Formatters.money(record.getEstimatedLoss()), TextUtil.orDash(record.getNote()));
            byReason.merge(record.getReason(), record.getEstimatedLoss(), BigDecimal::add);
            total = total.add(record.getEstimatedLoss());
        }
        report.setChartTitle("Loss by reason");
        for (Map.Entry<WasteReason, BigDecimal> entry : byReason.entrySet()) {
            report.addChartValue(entry.getKey().toString(), entry.getValue());
            report.addSummary(entry.getKey() + ": " + Formatters.money(entry.getValue()));
        }
        report.addSummary("Total estimated loss: " + Formatters.money(total) + " (" + records.size() + " record(s))");
    }
}
