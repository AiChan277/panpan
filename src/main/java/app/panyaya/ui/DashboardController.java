package app.panyaya.ui;

import app.panyaya.model.waste.WasteReason;
import app.panyaya.service.DashboardSummary;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dashboard: KPIs, charts and alerts, computed in the background from the saved data. */
public class DashboardController extends BaseController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM");

    @FXML
    private Label dateLabel;
    @FXML
    private Label updatedLabel;
    @FXML
    private FlowPane kpiPane;
    @FXML
    private BarChart<String, Number> revenueChart;
    @FXML
    private PieChart wasteChart;
    @FXML
    private Label wasteEmptyLabel;
    @FXML
    private ListView<DashboardSummary.Alert> lowStockList;
    @FXML
    private ListView<DashboardSummary.Alert> expiringIngredientList;
    @FXML
    private ListView<DashboardSummary.Alert> expiringProductList;
    @FXML
    private TableView<DashboardSummary.ProductDay> productionTable;
    @FXML
    private TableView<DashboardSummary.TopProduct> topTable;

    @Override
    protected void setUp() {
        productionTable.getColumns().setAll(java.util.Arrays.asList(
                UiUtil.<DashboardSummary.ProductDay>column("Product", 220, DashboardSummary.ProductDay::getProductName),
                UiUtil.<DashboardSummary.ProductDay>numberColumn("Produced", 90, d -> String.valueOf(d.getProduced())),
                UiUtil.<DashboardSummary.ProductDay>numberColumn("Sold", 80, d -> String.valueOf(d.getSold())),
                UiUtil.<DashboardSummary.ProductDay>numberColumn("Wasted", 80, d -> Formatters.quantity(d.getWasted())),
                UiUtil.<DashboardSummary.ProductDay>numberColumn("Sellable now", 110, d -> Formatters.quantity(d.getRemaining()))));
        UiUtil.placeholder(productionTable, "Nothing produced, sold or wasted today yet.");
        topTable.getColumns().setAll(java.util.Arrays.asList(
                UiUtil.<DashboardSummary.TopProduct>column("Product", 200, DashboardSummary.TopProduct::getName),
                UiUtil.<DashboardSummary.TopProduct>numberColumn("Qty", 70, t -> String.valueOf(t.getQuantity())),
                UiUtil.<DashboardSummary.TopProduct>numberColumn("Revenue", 120, t -> Formatters.money(t.getRevenue()))));
        UiUtil.placeholder(topTable, "No sales in the last 7 days.");
        for (ListView<DashboardSummary.Alert> list : java.util.Arrays.asList(lowStockList, expiringIngredientList,
                expiringProductList)) {
            list.setCellFactory(view -> new AlertCell());
        }
        lowStockList.setPlaceholder(new Label("✓ All ingredients above minimum."));
        expiringIngredientList.setPlaceholder(new Label("✓ Nothing expiring soon."));
        expiringProductList.setPlaceholder(new Label("✓ Nothing expiring soon."));
    }

    @Override
    public void refresh() {
        updatedLabel.setText("Updating...");
        // computed on a background copy of the saved data
        shell.tasks().run(window(), () -> ctx.dashboard().build(), this::show);
    }

    private void show(DashboardSummary summary) {
        dateLabel.setText("Today, " + Formatters.date(summary.getBusinessDate()));
        updatedLabel.setText("Updated " + LocalTime.now().withNano(0));

        kpiPane.getChildren().setAll(
                kpi("Revenue today", Formatters.money(summary.getRevenueToday()), "completed sales, voids excluded"),
                kpi("Transactions", String.valueOf(summary.getTransactionsToday()),
                        "average " + (summary.getTransactionsToday() == 0 ? "-" : Formatters.money(summary.getAverageTransaction()))),
                kpi("Gross profit (estimate)", Formatters.money(summary.getGrossProfitToday()), "revenue - ingredient cost"),
                kpi("Produced today", summary.getPiecesProducedToday() + " pcs",
                        summary.getProductionCompletedToday() + " batch(es) completed"),
                kpi("Waste today", Formatters.money(summary.getWasteCostToday()),
                        "7 days: " + Formatters.money(summary.getWasteCost7Days())),
                kpi("Products available", String.valueOf(summary.getProductsAvailable()),
                        summary.getProductsSoldOut() + " sold out"),
                kpi("Stock value", Formatters.money(summary.getIngredientStockValue().add(summary.getProductStockValue())),
                        "at risk (expired): " + Formatters.money(summary.getValueAtRisk())));

        // reuse one series: replacing series repeatedly can make JavaFX lose the axis labels
        if (revenueChart.getData().isEmpty()) {
            revenueChart.getData().add(new XYChart.Series<>());
        }
        List<XYChart.Data<String, Number>> points = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> day : summary.getRevenueLast14Days().entrySet()) {
            points.add(new XYChart.Data<>(DAY.format(day.getKey()), day.getValue()));
        }
        revenueChart.getData().get(0).getData().setAll(points);

        List<PieChart.Data> slices = new ArrayList<>();
        for (Map.Entry<WasteReason, BigDecimal> entry : summary.getWasteByReason7Days().entrySet()) {
            slices.add(new PieChart.Data(entry.getKey() + " " + Formatters.money(entry.getValue()),
                    entry.getValue().doubleValue()));
        }
        wasteChart.getData().setAll(slices);
        wasteChart.setVisible(!slices.isEmpty());
        wasteChart.setManaged(!slices.isEmpty());
        wasteEmptyLabel.setVisible(slices.isEmpty());
        wasteEmptyLabel.setManaged(slices.isEmpty());

        lowStockList.setItems(FXCollections.observableArrayList(summary.getLowStock()));
        expiringIngredientList.setItems(FXCollections.observableArrayList(summary.getExpiringIngredients()));
        expiringProductList.setItems(FXCollections.observableArrayList(summary.getExpiringProducts()));
        UiUtil.setItems(productionTable, summary.getProductionVsSales());
        UiUtil.setItems(topTable, summary.getTopProducts());
    }

    private static VBox kpi(String label, String value, String note) {
        Label caption = new Label(label);
        caption.getStyleClass().add("kpi-label");
        Label number = new Label(value);
        number.getStyleClass().add("kpi-value");
        Label detail = new Label(note);
        detail.getStyleClass().add("kpi-note");
        VBox card = new VBox(caption, number, detail);
        card.getStyleClass().add("kpi-card");
        return card;
    }

    /** Two-line list cell: bold title + detail; expired items get a warning symbol. */
    private static class AlertCell extends ListCell<DashboardSummary.Alert> {
        @Override
        protected void updateItem(DashboardSummary.Alert alert, boolean empty) {
            super.updateItem(alert, empty);
            if (empty || alert == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label((alert.getTitle().startsWith("EXPIRED") ? "✖ " : "⚠ ") + alert.getTitle());
            title.setStyle("-fx-font-weight: bold;");
            Label detail = new Label(alert.getDetail());
            detail.getStyleClass().add("hint");
            setGraphic(new VBox(1, title, detail));
            setText(null);
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onOpenIngredients() {
        shell.showView("Ingredients");
    }

    @FXML
    private void onOpenInventory() {
        shell.showView("Inventory");
    }

    @FXML
    private void onOpenWaste() {
        shell.showView("Waste");
    }
}
