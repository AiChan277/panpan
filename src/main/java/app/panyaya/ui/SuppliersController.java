package app.panyaya.ui;

import app.panyaya.model.supplier.GoodsReceipt;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.TextUtil;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Arrays;

/** Suppliers and their delivery history. */
public class SuppliersController extends BaseController {

    @FXML
    private TextField searchField;
    @FXML
    private CheckBox inactiveCheck;
    @FXML
    private TableView<Supplier> table;
    @FXML
    private Label historyTitle;
    @FXML
    private TableView<GoodsReceipt> historyTable;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<Supplier>column("Supplier", 230, Supplier::getName),
                UiUtil.<Supplier>column("Contact person", 150, s -> TextUtil.orDash(s.getContactPerson())),
                UiUtil.<Supplier>column("Phone", 130, s -> TextUtil.orDash(s.getPhone())),
                UiUtil.<Supplier>column("E-mail", 180, s -> TextUtil.orDash(s.getEmail())),
                UiUtil.<Supplier>column("Address", 200, s -> TextUtil.orDash(s.getAddress())),
                UiUtil.<Supplier>badgeColumn("Status", 90, s -> s.isActive() ? "Active" : "Inactive")));
        UiUtil.placeholder(table, "No suppliers yet.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, supplier) -> showHistory(supplier));
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                onEdit();
            }
        });
        historyTable.getColumns().setAll(Arrays.asList(
                UiUtil.<GoodsReceipt>column("Receipt", 160, GoodsReceipt::getReceiptNo),
                UiUtil.<GoodsReceipt>column("Received", 120, r -> Formatters.date(r.getReceivedDate())),
                UiUtil.<GoodsReceipt>column("Invoice", 120, r -> TextUtil.orDash(r.getInvoiceNo())),
                UiUtil.<GoodsReceipt>column("Items", 360, r -> {
                    StringBuilder items = new StringBuilder();
                    r.getLines().forEach(l -> items.append(items.length() > 0 ? ", " : "").append(l.getItemName()));
                    return items.toString();
                }),
                UiUtil.<GoodsReceipt>numberColumn("Total", 120, r -> Formatters.money(r.getTotalCost()))));
        UiUtil.placeholder(historyTable, "No deliveries from this supplier yet.");
        searchField.textProperty().addListener((obs, old, text) -> refresh());
        inactiveCheck.setOnAction(e -> refresh());
    }

    @Override
    public void refresh() {
        UiUtil.setItems(table, ctx.suppliers().search(searchField.getText(), inactiveCheck.isSelected()));
        showHistory(table.getSelectionModel().getSelectedItem());
    }

    private void showHistory(Supplier supplier) {
        if (supplier == null) {
            historyTable.getItems().clear();
            historyTitle.setText("Deliveries of the selected supplier");
            return;
        }
        historyTitle.setText("Deliveries from " + supplier.getName());
        UiUtil.setItems(historyTable, ctx.suppliers().receiptsOf(supplier.getId()));
    }

    private boolean edit(Supplier existing) {
        boolean editing = existing != null;
        FormDialog form = new FormDialog(window(), editing ? "Edit supplier" : "New supplier", "Save supplier");
        TextField name = form.add("Name *", UiUtil.field("e.g. CV Tepung Jaya", editing ? existing.getName() : ""));
        TextField contact = form.add("Contact person", UiUtil.field("", editing ? existing.getContactPerson() : ""));
        TextField phone = form.add("Phone", UiUtil.field("e.g. 0812-3456-7890", editing ? existing.getPhone() : ""));
        TextField email = form.add("E-mail", UiUtil.field("", editing ? existing.getEmail() : ""));
        TextField address = form.add("Address", UiUtil.field("", editing ? existing.getAddress() : ""));
        TextArea notes = form.add("Notes", UiUtil.area("", editing ? existing.getNotes() : "", 2));
        return form.show(() -> {
            if (editing) {
                ctx.suppliers().update(existing.getId(), name.getText(), contact.getText(), phone.getText(),
                        email.getText(), address.getText(), notes.getText());
            } else {
                ctx.suppliers().create(name.getText(), contact.getText(), phone.getText(), email.getText(),
                        address.getText(), notes.getText());
            }
        });
    }

    @FXML
    private void onNew() {
        if (edit(null)) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        Supplier supplier = table.getSelectionModel().getSelectedItem();
        if (supplier == null) {
            Dialogs.info(window(), "Edit supplier", "Select a supplier first.");
        } else if (edit(supplier)) {
            refresh();
        }
    }

    @FXML
    private void onToggle() {
        Supplier supplier = table.getSelectionModel().getSelectedItem();
        if (supplier != null) {
            attempt(() -> ctx.suppliers().setActive(supplier.getId(), !supplier.isActive()));
        }
    }

    @FXML
    private void onReceive() {
        if (ReceiveGoodsDialog.show(window(), ctx)) {
            refresh();
        }
    }
}
