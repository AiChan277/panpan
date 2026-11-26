package app.panyaya.ui;

import app.panyaya.model.StockKind;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.supplier.GoodsReceipt;
import app.panyaya.model.supplier.GoodsReceiptLine;
import app.panyaya.model.user.Permission;
import app.panyaya.model.user.User;
import app.panyaya.model.BakeryData;
import app.panyaya.report.DateRange;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.TextUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** Inventory: ingredient lots, finished-goods batches, the movement ledger and goods receipts. */
public class InventoryController extends BaseController {

    @FXML
    private TabPane tabs;
    @FXML
    private CheckBox emptyLotsCheck;
    @FXML
    private TableView<IngredientLot> lotsTable;
    @FXML
    private CheckBox emptyBatchesCheck;
    @FXML
    private TableView<FinishedGoodsBatch> batchesTable;
    @FXML
    private ComboBox<StockKind> kindFilter;
    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private Label ledgerCount;
    @FXML
    private TableView<InventoryMovement> ledgerTable;
    @FXML
    private TableView<GoodsReceipt> receiptsTable;
    @FXML
    private TextArea receiptDetail;
    @FXML
    private Button receiveButton;
    @FXML
    private Button adjustButton;

    @Override
    protected void setUp() {
        lotsTable.getColumns().setAll(Arrays.asList(
                UiUtil.<IngredientLot>column("Ingredient", 200, l -> ctx.inventory().itemName(StockKind.INGREDIENT, l.getItemId())),
                UiUtil.<IngredientLot>column("Lot", 140, IngredientLot::getLotCode),
                UiUtil.<IngredientLot>column("Received", 140, l -> Formatters.dateTime(l.getReceivedAt(), zone())),
                UiUtil.<IngredientLot>column("Expiry", 110, l -> l.describeExpiry(zone())),
                UiUtil.<IngredientLot>numberColumn("Initial", 90, l -> Formatters.quantity(l.getInitialQuantity())),
                UiUtil.<IngredientLot>numberColumn("Remaining", 90, l -> Formatters.quantity(l.getRemainingQuantity())),
                UiUtil.<IngredientLot>column("Unit", 50, l -> ctx.inventory().unitOf(StockKind.INGREDIENT, l.getItemId()).getSymbol()),
                UiUtil.<IngredientLot>numberColumn("Cost / unit", 90, l -> Formatters.unitCost(l.getUnitCost())),
                UiUtil.<IngredientLot>numberColumn("Value", 100, l -> Formatters.money(l.getRemainingValue())),
                UiUtil.<IngredientLot>badgeColumn("State", 100, l -> state(l.isDepleted(), l.isExpiredAt(now(), zone())))));
        UiUtil.placeholder(lotsTable, "No ingredient stock yet. Use \"Receive goods\" to record a delivery.");

        batchesTable.getColumns().setAll(Arrays.asList(
                UiUtil.<FinishedGoodsBatch>column("Product", 200, b -> ctx.inventory().itemName(StockKind.PRODUCT, b.getItemId())),
                UiUtil.<FinishedGoodsBatch>column("Batch", 170, FinishedGoodsBatch::getLotCode),
                UiUtil.<FinishedGoodsBatch>column("Source", 90, b -> b.getSource().toString()),
                UiUtil.<FinishedGoodsBatch>column("Made / received", 140, b -> Formatters.dateTime(b.getReceivedAt(), zone())),
                UiUtil.<FinishedGoodsBatch>column("Expires", 140, b -> Formatters.dateTime(b.getExpiresAt(), zone())),
                UiUtil.<FinishedGoodsBatch>numberColumn("Initial", 70, b -> Formatters.quantity(b.getInitialQuantity())),
                UiUtil.<FinishedGoodsBatch>numberColumn("Remaining", 80, b -> Formatters.quantity(b.getRemainingQuantity())),
                UiUtil.<FinishedGoodsBatch>numberColumn("Cost / pc", 90, b -> Formatters.money(b.getUnitCost())),
                UiUtil.<FinishedGoodsBatch>badgeColumn("State", 100, b -> state(b.isDepleted(), b.isExpiredAt(now(), zone())))));
        UiUtil.placeholder(batchesTable, "No finished goods yet. Complete a production order or receive resale products.");

        ledgerTable.getColumns().setAll(Arrays.asList(
                UiUtil.<InventoryMovement>column("Time", 150, m -> Formatters.dateTime(m.getOccurredAt(), zone())),
                UiUtil.<InventoryMovement>column("Type", 120, InventoryMovement::getTypeName),
                UiUtil.<InventoryMovement>column("Item", 180, m -> ctx.inventory().itemName(m.getStockKind(), m.getItemId())),
                UiUtil.<InventoryMovement>column("Lot / batch", 160, m -> ctx.inventory().lotCode(m.getStockKind(), m.getLotId())),
                UiUtil.<InventoryMovement>numberColumn("Quantity", 100, m -> (m.isIncoming() ? "+" : "-")
                        + Formatters.quantity(m.getQuantity(), ctx.inventory().unitOf(m.getStockKind(), m.getItemId()))),
                UiUtil.<InventoryMovement>numberColumn("Value", 100, m -> Formatters.money(m.getSignedValue())),
                UiUtil.<InventoryMovement>column("Reference", 150, m -> TextUtil.orDash(m.getReference())),
                UiUtil.<InventoryMovement>column("User", 90, m -> userName(m.getUserId())),
                UiUtil.<InventoryMovement>column("Note", 160, m -> TextUtil.orDash(m.getNote()))));
        UiUtil.placeholder(ledgerTable, "No movements in this period.");

        receiptsTable.getColumns().setAll(Arrays.asList(
                UiUtil.<GoodsReceipt>column("Receipt", 150, GoodsReceipt::getReceiptNo),
                UiUtil.<GoodsReceipt>column("Received", 110, r -> Formatters.date(r.getReceivedDate())),
                UiUtil.<GoodsReceipt>column("Supplier", 180, GoodsReceipt::getSupplierName),
                UiUtil.<GoodsReceipt>column("Invoice", 100, r -> TextUtil.orDash(r.getInvoiceNo())),
                UiUtil.<GoodsReceipt>numberColumn("Lines", 60, r -> String.valueOf(r.getLines().size())),
                UiUtil.<GoodsReceipt>numberColumn("Total", 110, r -> Formatters.money(r.getTotalCost()))));
        UiUtil.placeholder(receiptsTable, "No deliveries recorded yet.");
        receiptsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, receipt) -> showReceipt(receipt));

        UiUtil.allOption(kindFilter, "All stock", Arrays.asList(StockKind.values()));
        LocalDate today = LocalDate.ofInstant(now(), zone());
        fromPicker.setValue(today.minusDays(6));
        toPicker.setValue(today);
        emptyLotsCheck.setOnAction(e -> refresh());
        emptyBatchesCheck.setOnAction(e -> refresh());
        receiveButton.setDisable(!can(Permission.RECEIVE_STOCK));
        adjustButton.setDisable(!can(Permission.ADJUST_STOCK));
    }

    private ZoneId zone() {
        return ctx.settings().getSettings().zone();
    }

    private Instant now() {
        return ctx.getClock().instant();
    }

    private static String state(boolean depleted, boolean expired) {
        if (depleted) {
            return "Used up";
        }
        return expired ? "Expired" : "Usable";
    }

    private String userName(long userId) {
        User user = BakeryData.findById(ctx.getStore().getData().getUsers(), userId);
        return user == null ? "-" : user.getUsername();
    }

    @Override
    public void refresh() {
        UiUtil.setItems(lotsTable, ctx.inventory().getIngredientLots(null, emptyLotsCheck.isSelected()));
        UiUtil.setItems(batchesTable, ctx.inventory().getBatches(null, emptyBatchesCheck.isSelected()));
        UiUtil.setItems(receiptsTable, ctx.inventory().getReceipts());
        onShowLedger();
    }

    @FXML
    private void onShowLedger() {
        List<InventoryMovement> movements = ctx.inventory().getMovements(kindFilter.getValue(), null,
                fromPicker.getValue(), toPicker.getValue());
        UiUtil.setItems(ledgerTable, movements);
        ledgerCount.setText(movements.size() + " movement(s)");
    }

    private void showReceipt(GoodsReceipt receipt) {
        if (receipt == null) {
            receiptDetail.clear();
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(receipt.getReceiptNo()).append("  ").append(Formatters.date(receipt.getReceivedDate())).append('\n');
        text.append("Supplier: ").append(receipt.getSupplierName()).append('\n');
        text.append("Invoice:  ").append(TextUtil.orDash(receipt.getInvoiceNo())).append("\n\n");
        for (GoodsReceiptLine line : receipt.getLines()) {
            text.append("- ").append(line.getItemName()).append(": ").append(Formatters.quantity(line.getQuantity(), line.getUnit()))
                    .append(" (= ").append(Formatters.quantity(line.getBaseQuantity())).append(" base units)\n")
                    .append("  lot ").append(line.getLotCode()).append(", expiry ").append(Formatters.date(line.getExpiryDate()))
                    .append(", total ").append(Formatters.money(line.getTotalCost()))
                    .append(", ").append(Formatters.unitCost(line.getUnitCost())).append(" / unit\n");
        }
        text.append("\nTotal: ").append(Formatters.money(receipt.getTotalCost()));
        receiptDetail.setText(text.toString());
    }

    @FXML
    private void onReceive() {
        if (ReceiveGoodsDialog.show(window(), ctx)) {
            refresh();
        }
    }

    @FXML
    private void onAdjust() {
        int tab = tabs.getSelectionModel().getSelectedIndex();
        boolean changed = false;
        if (tab == 0 && lotsTable.getSelectionModel().getSelectedItem() != null) {
            changed = AdjustStockDialog.show(window(), ctx, StockKind.INGREDIENT, lotsTable.getSelectionModel().getSelectedItem());
        } else if (tab == 1 && batchesTable.getSelectionModel().getSelectedItem() != null) {
            changed = AdjustStockDialog.show(window(), ctx, StockKind.PRODUCT, batchesTable.getSelectionModel().getSelectedItem());
        } else {
            Dialogs.info(window(), "Adjust stock", "Select a lot (Ingredient lots tab) or a batch (Finished goods tab) first.");
        }
        if (changed) {
            refresh();
        }
    }

    @FXML
    private void onExportLedger() {
        Path file = Dialogs.chooseSaveFile(window(), "Export stock ledger", "stock-ledger.csv", "CSV file", ".csv",
                ctx.getPaths().getExportDir());
        if (file == null || fromPicker.getValue() == null || toPicker.getValue() == null) {
            return;
        }
        DateRange range = new DateRange(fromPicker.getValue(), toPicker.getValue());
        shell.tasks().run(window(), () -> {
            ctx.csv().exportMovements(file, range);
            return file;
        }, saved -> shell.setStatus("Exported " + saved));
    }
}
