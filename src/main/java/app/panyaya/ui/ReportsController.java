package app.panyaya.ui;

import app.panyaya.report.DateRange;
import app.panyaya.report.Report;
import app.panyaya.report.ReportGenerator;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reports. The screen does not know the individual reports: it shows any {@link ReportGenerator}
 * (polymorphism) and builds the table columns from the report's own column list.
 */
public class ReportsController extends BaseController {

    private static final List<String> PRESETS = Arrays.asList("Today", "Yesterday", "This week", "This month",
            "Last month", "Last 30 days", "Custom");

    @FXML
    private ComboBox<ReportGenerator> reportBox;
    @FXML
    private ComboBox<String> presetBox;
    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private Button generateButton;
    @FXML
    private Button exportButton;
    @FXML
    private Label descriptionLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private TableView<List<String>> table;
    @FXML
    private VBox summaryBox;
    @FXML
    private Label chartTitle;
    @FXML
    private BarChart<String, Number> chart;

    private Report current;

    @Override
    protected void setUp() {
        reportBox.getItems().setAll(ctx.reports().getGenerators());
        reportBox.setValue(reportBox.getItems().get(0));
        presetBox.getItems().setAll(PRESETS);
        presetBox.setValue("Today");
        applyPreset();
        presetBox.setOnAction(e -> applyPreset());
        reportBox.setOnAction(e -> {
            updateDescription();
            onGenerate();
        });
        fromPicker.setOnAction(e -> presetBox.setValue("Custom"));
        toPicker.setOnAction(e -> presetBox.setValue("Custom"));
        exportButton.setDisable(true);
        UiUtil.placeholder(table, "Choose a report and a period, then press Generate.");
        updateDescription();
    }

    @Override
    public void refresh() {
        onGenerate();
    }

    private void updateDescription() {
        ReportGenerator generator = reportBox.getValue();
        descriptionLabel.setText(generator.getDescription()
                + (generator.usesDateRange() ? "" : " (Shows the current stock; the period is not used.)"));
        boolean usesRange = generator.usesDateRange();
        presetBox.setDisable(!usesRange);
        fromPicker.setDisable(!usesRange);
        toPicker.setDisable(!usesRange);
    }

    private void applyPreset() {
        LocalDate today = LocalDate.ofInstant(ctx.getClock().instant(), ctx.settings().getSettings().zone());
        DateRange range;
        switch (presetBox.getValue()) {
            case "Yesterday":
                range = DateRange.yesterday(today);
                break;
            case "This week":
                range = DateRange.thisWeek(today);
                break;
            case "This month":
                range = DateRange.thisMonth(today);
                break;
            case "Last month":
                range = DateRange.lastMonth(today);
                break;
            case "Last 30 days":
                range = DateRange.lastDays(today, 30);
                break;
            case "Custom":
                return;
            default:
                range = DateRange.today(today);
        }
        fromPicker.setValue(range.getFrom());
        toPicker.setValue(range.getTo());
    }

    @FXML
    private void onGenerate() {
        if (fromPicker.getValue() == null || toPicker.getValue() == null) {
            return;
        }
        if (toPicker.getValue().isBefore(fromPicker.getValue())) {
            Dialogs.info(window(), "Reports", "The end date is before the start date.");
            return;
        }
        ReportGenerator generator = reportBox.getValue();
        DateRange range = new DateRange(fromPicker.getValue(), toPicker.getValue());
        generateButton.setDisable(true);
        // generated on a background thread from a copy of the saved data
        shell.tasks().run(() -> ctx.reports().generate(generator, range), report -> {
            generateButton.setDisable(false);
            show(report);
        }, error -> {
            generateButton.setDisable(false);
            Dialogs.error(window(), error);
        });
    }

    private void show(Report report) {
        current = report;
        exportButton.setDisable(false);
        titleLabel.setText(report.getTitle() + "  ·  " + (reportBox.getValue().usesDateRange()
                ? report.getRange().toString() : "current stock, "
                + Formatters.dateTime(report.getGeneratedAt(), ctx.settings().getSettings().zone())));
        table.getColumns().clear();
        List<String> columns = report.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            final int index = i;
            TableColumn<List<String>, String> column = new TableColumn<>(columns.get(i));
            column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(index)));
            column.setPrefWidth(i == 0 ? 180 : 115);
            String sample = report.getRows().isEmpty() ? "" : report.getRows().get(0).get(i);
            // numbers like "24", "1.25 kg", "61.1 %" are right-aligned; dates such as "04 Nov 2026" are not
            if (sample.startsWith("Rp") || sample.startsWith("-Rp") || sample.matches("[+-]?\\d+(\\.\\d+)?( [a-zA-Z%]{1,5})?")) {
                column.getStyleClass().add("number-column");
                column.setComparator(UiUtil.NUMERIC);
            }
            table.getColumns().add(column);
        }
        table.setItems(FXCollections.observableArrayList(report.getRows()));
        UiUtil.placeholder(table, "No data for this period.");

        summaryBox.getChildren().clear();
        for (String line : report.getSummary()) {
            Label label = new Label(line);
            label.setStyle("-fx-font-weight: bold;");
            summaryBox.getChildren().add(label);
        }

        chartTitle.setText(report.getChartTitle());
        if (chart.getData().isEmpty()) {
            chart.getData().add(new XYChart.Series<>());
        }
        XYChart.Series<String, Number> series = chart.getData().get(0);
        series.getData().clear();
        int count = 0;
        for (Map.Entry<String, BigDecimal> entry : report.getChartData().entrySet()) {
            if (count++ >= 31) {
                break; // keep the chart readable
            }
            String label = entry.getKey().length() > 18 ? entry.getKey().substring(0, 17) + "." : entry.getKey();
            series.getData().add(new XYChart.Data<>(label, entry.getValue()));
        }
    }

    @FXML
    private void onExport() {
        if (current == null) {
            return;
        }
        String name = current.getTitle().toLowerCase().replace(' ', '-') + "-"
                + Formatters.ISO_DATE.format(current.getRange().getFrom()) + ".csv";
        Path file = Dialogs.chooseSaveFile(window(), "Export report", name, "CSV file", ".csv", ctx.getPaths().getExportDir());
        if (file == null) {
            return;
        }
        Report report = current;
        shell.tasks().run(window(), () -> {
            ctx.csv().exportReport(report, file);
            return file;
        }, saved -> {
            shell.setStatus("Report exported to " + saved);
            Dialogs.info(window(), "Export", "Saved " + saved.getFileName() + " (opens in Excel or LibreOffice).");
        });
    }
}
