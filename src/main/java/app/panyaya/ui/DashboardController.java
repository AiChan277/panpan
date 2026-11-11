package app.panyaya.ui;

import app.panyaya.model.waste.WasteReason;
import app.panyaya.service.DashboardSummary;
import app.panyaya.ui.common.Badge;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.CardGrid;
import app.panyaya.ui.common.EmptyState;
import app.panyaya.ui.common.KpiCard;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dashboard: key figures, revenue and waste charts and stock alerts, computed in the background from
 * the saved data. Every number shown comes from {@link DashboardSummary}; nothing is estimated here
 * except simple ratios of those numbers (change vs. yesterday, margin, share of waste).
 */
public class DashboardController extends BaseController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final PseudoClass TODAY = PseudoClass.getPseudoClass("today");
    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    @FXML
    private Label dateLabel;
    @FXML
    private Label updatedLabel;
    @FXML
    private CardGrid kpiPane;
    @FXML
    private BarChart<String, Number> revenueChart;
    @FXML
    private NumberAxis revenueAxis;
    @FXML
    private VBox revenueEmpty;
    @FXML
    private Label revenueSummary;
    @FXML
    private Label wasteTotalLabel;
    @FXML
    private Label wasteNote;
    @FXML
    private VBox wasteBox;
    @FXML
    private Label alertSummary;
    @FXML
    private Label lowStockCount;
    @FXML
    private Label expiringIngredientCount;
    @FXML
    private Label expiringProductCount;
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
        lowStockList.setPlaceholder(allClear("All ingredients are above their minimum."));
        expiringIngredientList.setPlaceholder(allClear("No ingredient lot expires soon."));
        expiringProductList.setPlaceholder(allClear("No product batch expires soon."));

        revenueAxis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number value) {
                return compactMoney(value.doubleValue());
            }

            @Override
            public Number fromString(String text) {
                return 0;
            }
        });
        revenueEmpty.getChildren().setAll(new EmptyState("saleshistory", "No sales in the last 14 days",
                "Completed sales appear here day by day."));
    }

    private static Node allClear(String text) {
        EmptyState state = new EmptyState("check", null, text);
        state.getStyleClass().add("success");
        return state;
    }

    /** Axis labels: "Rp 750 rb", "Rp 1,2 jt" (Indonesian thousand / million). */
    private static String compactMoney(double value) {
        if (value == 0) {
            return "0";
        }
        if (Math.abs(value) >= 1_000_000) {
            String number = BigDecimal.valueOf(value / 1_000_000).setScale(1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString().replace('.', ',');
            return "Rp " + number + " jt";
        }
        if (Math.abs(value) >= 1_000) {
            return "Rp " + Math.round(value / 1_000) + " rb";
        }
        return "Rp " + Math.round(value);
    }

    @Override
    public void refresh() {
        updatedLabel.setText("Updating...");
        // computed on a background copy of the saved data
        shell.tasks().run(window(), () -> ctx.dashboard().build(), this::show);
    }

    private void show(DashboardSummary summary) {
        dateLabel.setText("Today  ·  " + LONG_DAY.format(summary.getBusinessDate()));
        updatedLabel.setText("Updated " + LocalTime.now().withNano(0));
        showKpis(summary);
        showRevenue(summary);
        showWaste(summary);
        showAlerts(summary);
        UiUtil.setItems(productionTable, summary.getProductionVsSales());
        UiUtil.setItems(topTable, summary.getTopProducts());
    }

    private void showKpis(DashboardSummary summary) {
        BigDecimal revenue = summary.getRevenueToday();
        BigDecimal yesterday = summary.getRevenueLast14Days().get(summary.getBusinessDate().minusDays(1));
        KpiCard revenueCard = new KpiCard("pointofsale", "Revenue today", Formatters.money(revenue),
                yesterday != null && yesterday.signum() > 0 ? "vs. yesterday " + Formatters.money(yesterday)
                        : "Completed sales, voids excluded");
        if (yesterday != null && yesterday.signum() > 0) {
            BigDecimal change = revenue.subtract(yesterday).multiply(BigDecimal.valueOf(100))
                    .divide(yesterday, 0, RoundingMode.HALF_UP);
            revenueCard.delta((change.signum() > 0 ? "+" : "") + change.toPlainString() + " %",
                    change.signum() > 0 ? Badge.Tone.SUCCESS : change.signum() < 0 ? Badge.Tone.DANGER : Badge.Tone.NEUTRAL);
        }

        int transactions = summary.getTransactionsToday();
        KpiCard transactionCard = new KpiCard("saleshistory", "Transactions", String.valueOf(transactions),
                transactions == 0 ? "No sales yet today"
                        : "Average " + Formatters.money(summary.getAverageTransaction()) + " per sale");

        BigDecimal profit = summary.getGrossProfitToday();
        KpiCard profitCard = new KpiCard("trend-up", "Gross profit (estimate)", Formatters.money(profit),
                "Revenue minus ingredient cost");
        if (revenue.signum() > 0) {
            BigDecimal margin = profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 0, RoundingMode.HALF_UP);
            profitCard.delta(margin.toPlainString() + " % margin", Badge.Tone.NEUTRAL);
        }

        BigDecimal stockValue = summary.getIngredientStockValue().add(summary.getProductStockValue());
        boolean atRisk = summary.getValueAtRisk().signum() > 0;
        KpiCard stockCard = new KpiCard("inventory", "Stock value", Formatters.money(stockValue),
                atRisk ? Formatters.money(summary.getValueAtRisk()) + " expired - write off"
                        : "Ingredients " + Formatters.money(summary.getIngredientStockValue())
                        + "  ·  products " + Formatters.money(summary.getProductStockValue()))
                .tone(atRisk ? Badge.Tone.DANGER : null);

        KpiCard producedCard = new KpiCard("production", "Produced today", summary.getPiecesProducedToday() + " pcs",
                summary.getProductionCompletedToday() + " batch(es) completed");

        boolean wasteToday = summary.getWasteCostToday().signum() > 0;
        KpiCard wasteCard = new KpiCard("wastemanagement", "Waste today", Formatters.money(summary.getWasteCostToday()),
                "Last 7 days: " + Formatters.money(summary.getWasteCost7Days()))
                .tone(wasteToday ? Badge.Tone.WARNING : null);

        int soldOut = summary.getProductsSoldOut();
        KpiCard availableCard = new KpiCard("products", "Products available", String.valueOf(summary.getProductsAvailable()),
                soldOut == 0 ? "None sold out" : soldOut + " sold out")
                .tone(soldOut > 0 ? Badge.Tone.WARNING : null);

        kpiPane.getChildren().setAll(revenueCard, transactionCard, profitCard, stockCard,
                producedCard, wasteCard, availableCard);
    }

    private void showRevenue(DashboardSummary summary) {
        // reuse one series: replacing series repeatedly can make JavaFX lose the axis labels
        if (revenueChart.getData().isEmpty()) {
            revenueChart.getData().add(new XYChart.Series<>());
        }
        List<XYChart.Data<String, Number>> points = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int days = 0;
        for (Map.Entry<LocalDate, BigDecimal> day : summary.getRevenueLast14Days().entrySet()) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(DAY.format(day.getKey()), day.getValue());
            boolean today = day.getKey().equals(summary.getBusinessDate());
            String tip = LONG_DAY.format(day.getKey()) + "\n" + Formatters.money(day.getValue());
            point.nodeProperty().addListener((obs, old, node) -> decorateBar(node, today, tip));
            points.add(point);
            total = total.add(day.getValue());
            days++;
        }
        revenueChart.getData().get(0).getData().setAll(points); // bars are decorated as JavaFX creates them
        boolean empty = total.signum() == 0;
        revenueChart.setOpacity(empty ? 0.35 : 1);
        revenueEmpty.setVisible(empty);
        revenueSummary.setText(empty ? "No completed sales in this period."
                : Formatters.money(total) + " in 14 days  ·  average "
                + Formatters.money(total.divide(BigDecimal.valueOf(Math.max(1, days)), 0, RoundingMode.HALF_UP)) + " per day");
    }

    private static void decorateBar(Node bar, boolean today, String tip) {
        if (bar == null || bar.getProperties().containsKey("decorated")) {
            return;
        }
        bar.getProperties().put("decorated", Boolean.TRUE);
        // a pseudo-class, because BarChart resets the bar's style classes after creating it
        bar.pseudoClassStateChanged(TODAY, today);
        Tooltip tooltip = new Tooltip(tip);
        tooltip.setShowDelay(Duration.millis(120));
        Tooltip.install(bar, tooltip);
    }

    private void showWaste(DashboardSummary summary) {
        List<Map.Entry<WasteReason, BigDecimal>> reasons = new ArrayList<>(summary.getWasteByReason7Days().entrySet());
        reasons.removeIf(entry -> entry.getValue().signum() <= 0);
        reasons.sort(Map.Entry.<WasteReason, BigDecimal>comparingByValue().reversed());
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<WasteReason, BigDecimal> entry : reasons) {
            total = total.add(entry.getValue());
        }
        wasteBox.getChildren().clear();
        if (reasons.isEmpty()) {
            wasteTotalLabel.setText(Formatters.money(BigDecimal.ZERO));
            wasteNote.setText("Estimated loss at cost price.");
            EmptyState none = new EmptyState("check", "No waste in the last 7 days",
                    "Waste recorded on the Waste screen is grouped here by reason.");
            none.getStyleClass().add("success");
            VBox.setVgrow(none, Priority.ALWAYS);
            none.setMaxHeight(Double.MAX_VALUE);
            wasteBox.getChildren().add(none);
            return;
        }
        wasteTotalLabel.setText(Formatters.money(total));
        wasteNote.setText("Estimated loss at cost price, " + reasons.size() + " reason(s).");
        for (Map.Entry<WasteReason, BigDecimal> entry : reasons) {
            double share = entry.getValue().doubleValue() / total.doubleValue();
            wasteBox.getChildren().add(wasteRow(entry.getKey().toString(), entry.getValue(), share));
        }
    }

    private static VBox wasteRow(String reason, BigDecimal amount, double share) {
        Label name = new Label(reason);
        name.getStyleClass().add("waste-reason");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label value = new Label(Formatters.money(amount));
        value.getStyleClass().add("waste-amount");
        Label percent = new Label(Math.round(share * 100) + " %");
        percent.getStyleClass().add("waste-share");
        HBox head = new HBox(name, spacer, value, percent);
        head.getStyleClass().add("waste-row-head");

        Region fill = new Region();
        fill.getStyleClass().add("bar-fill");
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("bar-track");
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        fill.prefWidthProperty().bind(track.widthProperty().multiply(Math.max(0.02, share)));

        VBox row = new VBox(head, track);
        row.getStyleClass().add("waste-row");
        Tooltip.install(row, new Tooltip(reason + ": " + Formatters.money(amount) + " (" + Math.round(share * 100) + " %)"));
        return row;
    }

    private void showAlerts(DashboardSummary summary) {
        List<DashboardSummary.Alert> low = urgentFirst(summary.getLowStock());
        List<DashboardSummary.Alert> ingredients = urgentFirst(summary.getExpiringIngredients());
        List<DashboardSummary.Alert> products = urgentFirst(summary.getExpiringProducts());
        lowStockList.setItems(FXCollections.observableArrayList(low));
        expiringIngredientList.setItems(FXCollections.observableArrayList(ingredients));
        expiringProductList.setItems(FXCollections.observableArrayList(products));
        count(lowStockCount, low.size());
        count(expiringIngredientCount, ingredients.size());
        count(expiringProductCount, products.size());
        long urgent = low.stream().filter(a -> tone(a) == Badge.Tone.DANGER).count()
                + ingredients.stream().filter(a -> tone(a) == Badge.Tone.DANGER).count()
                + products.stream().filter(a -> tone(a) == Badge.Tone.DANGER).count();
        int all = low.size() + ingredients.size() + products.size();
        alertSummary.setText(all == 0 ? "Nothing needs attention right now."
                : all + " item(s) need attention" + (urgent > 0 ? ", " + urgent + " of them now (out of stock or expired)" : ""));
    }

    private static void count(Label label, int count) {
        label.setText(String.valueOf(count));
        label.setVisible(count > 0);
        label.setManaged(count > 0);
    }

    /** Expired and out-of-stock items first: they need action now. */
    private static List<DashboardSummary.Alert> urgentFirst(List<DashboardSummary.Alert> alerts) {
        List<DashboardSummary.Alert> sorted = new ArrayList<>(alerts);
        sorted.sort(Comparator.comparingInt(alert -> tone(alert) == Badge.Tone.DANGER ? 0 : 1));
        return sorted;
    }

    private static Badge.Tone tone(DashboardSummary.Alert alert) {
        if (alert.getTitle().startsWith("EXPIRED") || alert.getDetail().startsWith("Out of stock")) {
            return Badge.Tone.DANGER;
        }
        return Badge.Tone.WARNING;
    }

    /** Alert row: status badge, item name and what to do. */
    private static class AlertCell extends ListCell<DashboardSummary.Alert> {
        @Override
        protected void updateItem(DashboardSummary.Alert alert, boolean empty) {
            super.updateItem(alert, empty);
            setText(null);
            if (empty || alert == null) {
                setGraphic(null);
                return;
            }
            boolean expired = alert.getTitle().startsWith("EXPIRED");
            String name = expired ? alert.getTitle().substring("EXPIRED".length()).trim() : alert.getTitle();
            String status;
            if (expired) {
                status = "Expired";
            } else if (alert.getDetail().startsWith("Out of stock")) {
                status = "Out of stock";
            } else if (alert.getDetail().startsWith("Low")) {
                status = "Low";
            } else {
                status = "Expiring";
            }
            Label title = new Label(name);
            title.getStyleClass().add("alert-title");
            Label detail = new Label(alert.getDetail());
            detail.getStyleClass().add("alert-detail");
            detail.setWrapText(true);
            VBox text = new VBox(2, title, detail);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            Badge badge = new Badge(status, tone(alert));
            HBox row = new HBox(10, text, badge);
            row.getStyleClass().add("alert-row");
            row.setPrefWidth(0);
            setGraphic(row);
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
