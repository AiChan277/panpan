package app.panyaya.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of a report: a table (columns + rows of text), summary lines and optional chart data.
 * The same object is shown in the UI table, drawn as a chart and exported to CSV.
 */
public class Report {

    private final String title;
    private final DateRange range;
    private final List<String> columns;
    private final List<List<String>> rows = new ArrayList<>();
    private final List<String> summary = new ArrayList<>();
    private final Map<String, BigDecimal> chartData = new LinkedHashMap<>();
    private final Instant generatedAt;
    private String chartTitle = "";

    public Report(String title, DateRange range, List<String> columns, Instant generatedAt) {
        this.title = title;
        this.range = range;
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        this.generatedAt = generatedAt;
    }

    public void addRow(String... values) {
        if (values.length != columns.size()) {
            throw new IllegalArgumentException("Row has " + values.length + " values, report has "
                    + columns.size() + " columns");
        }
        rows.add(Collections.unmodifiableList(Arrays.asList(values)));
    }

    public void addSummary(String line) {
        summary.add(line);
    }

    public void setChartTitle(String chartTitle) {
        this.chartTitle = chartTitle;
    }

    /** Adds to (or creates) a chart bar. */
    public void addChartValue(String label, BigDecimal value) {
        chartData.merge(label, value, BigDecimal::add);
    }

    public String getTitle() {
        return title;
    }

    public DateRange getRange() {
        return range;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public List<String> getSummary() {
        return Collections.unmodifiableList(summary);
    }

    public String getChartTitle() {
        return chartTitle;
    }

    public Map<String, BigDecimal> getChartData() {
        return Collections.unmodifiableMap(chartData);
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
