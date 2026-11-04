package app.panyaya.ui;

import app.panyaya.model.Money;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleStatus;
import app.panyaya.model.user.Permission;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Sales history with filters, receipt view and void (Owner, same day). */
public class SalesHistoryController extends BaseController {

    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<PaymentMethod> methodFilter;
    @FXML
    private ComboBox<SaleStatus> statusFilter;
    @FXML
    private Label totalsLabel;
    @FXML
    private TableView<Sale> table;
    @FXML
    private TextArea receiptArea;
    @FXML
    private Button voidButton;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<Sale>column("Time", 130, s -> Formatters.dateTime(s.getOccurredAt(), ctx.settings().getSettings().zone())),
                UiUtil.<Sale>column("Sale no.", 150, Sale::getSaleNo),
                UiUtil.<Sale>column("Cashier", 110, Sale::getCashierName),
                UiUtil.<Sale>numberColumn("Items", 60, s -> String.valueOf(s.getItemCount())),
                UiUtil.<Sale>numberColumn("Total", 110, s -> Formatters.money(s.getTotal())),
                UiUtil.<Sale>column("Payment", 110, s -> String.valueOf(s.getPaymentMethod())),
                UiUtil.<Sale>column("Status", 110, s -> UiUtil.statusText(s.getStatus().toString()))));
        UiUtil.placeholder(table, "No sales in this period.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sale) ->
                receiptArea.setText(sale == null ? "" : ctx.sales().receiptText(sale)));

        LocalDate today = LocalDate.ofInstant(ctx.getClock().instant(), ctx.settings().getSettings().zone());
        fromPicker.setValue(today);
        toPicker.setValue(today);
        UiUtil.allOption(methodFilter, "All payments", Arrays.asList(PaymentMethod.values()));
        UiUtil.allOption(statusFilter, "All statuses", Arrays.asList(SaleStatus.COMPLETED, SaleStatus.VOIDED));
        fromPicker.setOnAction(e -> refresh());
        toPicker.setOnAction(e -> refresh());
        methodFilter.setOnAction(e -> refresh());
        statusFilter.setOnAction(e -> refresh());
        searchField.textProperty().addListener((obs, old, text) -> refresh());
        voidButton.setDisable(!can(Permission.VOID_SALE));
    }

    @Override
    public void refresh() {
        List<Sale> sales = ctx.sales().search(fromPicker.getValue(), toPicker.getValue(), searchField.getText(),
                methodFilter.getValue(), statusFilter.getValue());
        UiUtil.setItems(table, sales);
        BigDecimal revenue = Money.ZERO;
        int completed = 0;
        for (Sale sale : sales) {
            if (sale.isCompleted()) {
                revenue = revenue.add(sale.getTotal());
                completed++;
            }
        }
        totalsLabel.setText(completed + " completed sale(s) · " + Formatters.money(revenue)
                + (can(Permission.VIEW_ALL_SALES) ? "" : " · your own sales only"));
    }

    @FXML
    private void onVoid() {
        Sale sale = table.getSelectionModel().getSelectedItem();
        if (sale == null) {
            Dialogs.info(window(), "Void sale", "Select a sale first.");
            return;
        }
        String reason = Dialogs.ask(window(), "Void sale " + sale.getSaleNo(),
                "Why is this sale voided? (" + Formatters.money(sale.getTotal()) + ")", "");
        if (reason != null) {
            attempt(() -> ctx.sales().voidSale(sale.getId(), reason));
        }
    }

    @FXML
    private void onSaveReceipt() {
        Sale sale = table.getSelectionModel().getSelectedItem();
        if (sale == null) {
            return;
        }
        Path file = Dialogs.chooseSaveFile(window(), "Save receipt", sale.getSaleNo() + ".txt", "Text file", ".txt",
                ctx.getPaths().getExportDir());
        if (file != null) {
            attempt(() -> {
                ctx.sales().saveReceipt(sale, file);
                shell.setStatus("Receipt saved to " + file);
            });
        }
    }
}
