package app.panyaya.ui;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.model.StockKind;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.StockLot;
import app.panyaya.model.product.Product;
import app.panyaya.model.user.Permission;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import app.panyaya.util.TextUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Waste recording, end-of-day write-off and waste history. */
public class WasteController extends BaseController {

    /** Item choice: an ingredient or a product, shown by name. */
    private static class ItemChoice {
        final StockKind kind;
        final long id;
        final String name;

        ItemChoice(StockKind kind, long id, String name) {
            this.kind = kind;
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Lot choice with a readable label (code, remaining, expiry). */
    private static class LotChoice {
        final StockLot lot;
        final String label;

        LotChoice(StockLot lot, String label) {
            this.lot = lot;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @FXML
    private ComboBox<StockKind> kindBox;
    @FXML
    private ComboBox<ItemChoice> itemBox;
    @FXML
    private ComboBox<LotChoice> lotBox;
    @FXML
    private Label quantityCaption;
    @FXML
    private TextField quantityField;
    @FXML
    private ComboBox<WasteReason> reasonBox;
    @FXML
    private TextField noteField;
    @FXML
    private Label lossLabel;
    @FXML
    private Label endOfDayHint;
    @FXML
    private TableView<FinishedGoodsBatch> endOfDayTable;
    @FXML
    private Button writeOffButton;
    @FXML
    private Label historyTotal;
    @FXML
    private TableView<WasteRecord> historyTable;

    @Override
    protected void setUp() {
        List<StockKind> kinds = new ArrayList<>();
        kinds.add(StockKind.PRODUCT);
        if (can(Permission.RECORD_INGREDIENT_WASTE)) {
            kinds.add(StockKind.INGREDIENT);
        }
        kindBox.getItems().setAll(kinds);
        kindBox.setValue(StockKind.PRODUCT);
        reasonBox.getItems().setAll(WasteReason.values());
        reasonBox.setValue(WasteReason.DAMAGED);
        kindBox.setOnAction(e -> fillItems());
        itemBox.setOnAction(e -> fillLots());
        lotBox.setOnAction(e -> updateLoss());
        quantityField.textProperty().addListener((obs, old, text) -> updateLoss());

        endOfDayTable.getColumns().setAll(Arrays.asList(
                UiUtil.<FinishedGoodsBatch>column("Product", 170, b -> ctx.inventory().itemName(StockKind.PRODUCT, b.getItemId())),
                UiUtil.<FinishedGoodsBatch>column("Batch", 150, FinishedGoodsBatch::getLotCode),
                UiUtil.<FinishedGoodsBatch>column("Expires", 130, b -> Formatters.dateTime(b.getExpiresAt(), zone())),
                UiUtil.<FinishedGoodsBatch>numberColumn("Left", 50, b -> Formatters.quantity(b.getRemainingQuantity())),
                UiUtil.<FinishedGoodsBatch>numberColumn("Loss", 90, b -> Formatters.money(b.getRemainingValue()))));
        endOfDayTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        UiUtil.placeholder(endOfDayTable, "✓ Nothing to write off tonight.");

        historyTable.getColumns().setAll(Arrays.asList(
                UiUtil.<WasteRecord>column("Time", 130, w -> Formatters.dateTime(w.getOccurredAt(), zone())),
                UiUtil.<WasteRecord>column("No.", 140, WasteRecord::getWasteNo),
                UiUtil.<WasteRecord>column("Kind", 80, w -> w.getStockKind().toString()),
                UiUtil.<WasteRecord>column("Item", 180, WasteRecord::getItemName),
                UiUtil.<WasteRecord>column("Lot / batch", 150, w -> TextUtil.orDash(w.getLotCode())),
                UiUtil.<WasteRecord>numberColumn("Quantity", 90, w -> Formatters.quantity(w.getQuantity(), w.getUnit())),
                UiUtil.<WasteRecord>column("Reason", 130, w -> w.getReason().toString()),
                UiUtil.<WasteRecord>numberColumn("Est. loss", 100, w -> Formatters.money(w.getEstimatedLoss())),
                UiUtil.<WasteRecord>column("Note", 200, w -> TextUtil.orDash(w.getNote()))));
        UiUtil.placeholder(historyTable, "No waste recorded in the last 30 days.");
    }

    private ZoneId zone() {
        return ctx.settings().getSettings().zone();
    }

    @Override
    public void refresh() {
        fillItems();
        endOfDayTable.getItems().setAll(ctx.waste().endOfDayCandidates());
        endOfDayHint.setText("Batches already expired or expiring before "
                + String.format("%02d:00", ctx.settings().getSettings().getNearExpiryHours())
                + " tomorrow. Select them (Ctrl/Shift for several) and write off in one step: expired ones as "
                + "EXPIRED, the rest as UNSOLD.");
        LocalDate today = LocalDate.ofInstant(ctx.getClock().instant(), zone());
        List<WasteRecord> history = ctx.waste().search(today.minusDays(29), today, null, null);
        UiUtil.setItems(historyTable, history);
        BigDecimal total = Money.ZERO;
        for (WasteRecord record : history) {
            total = total.add(record.getEstimatedLoss());
        }
        historyTotal.setText(history.size() + " record(s) · total loss " + Formatters.money(total));
    }

    private void fillItems() {
        List<ItemChoice> items = new ArrayList<>();
        if (kindBox.getValue() == StockKind.INGREDIENT) {
            for (Ingredient ingredient : ctx.ingredients().getActiveIngredients()) {
                if (!ctx.inventory().getIngredientLots(ingredient.getId(), false).isEmpty()) {
                    items.add(new ItemChoice(StockKind.INGREDIENT, ingredient.getId(), ingredient.getName()));
                }
            }
            quantityCaption.setText("Quantity (base unit)");
        } else {
            for (Product product : ctx.products().search("", null, null, true)) {
                if (!ctx.inventory().getBatches(product.getId(), false).isEmpty()) {
                    items.add(new ItemChoice(StockKind.PRODUCT, product.getId(), product.getName()));
                }
            }
            quantityCaption.setText("Quantity (pieces)");
        }
        itemBox.getItems().setAll(items);
        itemBox.setPromptText(items.isEmpty() ? "nothing in stock" : "choose...");
        fillLots();
    }

    private void fillLots() {
        lotBox.getItems().clear();
        ItemChoice item = itemBox.getValue();
        if (item != null) {
            List<LotChoice> lots = new ArrayList<>();
            List<? extends StockLot> source = item.kind == StockKind.INGREDIENT
                    ? ctx.inventory().getIngredientLots(item.id, false) : ctx.inventory().getBatches(item.id, false);
            List<StockLot> sorted = new ArrayList<>(source);
            // default = earliest expiring lot
            sorted.sort(Comparator.comparing((StockLot l) -> {
                java.time.Instant key = l.getExpirySortKey(zone());
                return key == null ? java.time.Instant.MAX : key;
            }).thenComparing(BaseEntity::getId));
            for (StockLot lot : sorted) {
                boolean expired = lot.isExpiredAt(ctx.getClock().instant(), zone());
                lots.add(new LotChoice(lot, lot.getLotCode() + " · " + Formatters.quantity(lot.getRemainingQuantity())
                        + " left · " + (expired ? "EXPIRED " : "expires ") + lot.describeExpiry(zone())));
            }
            lotBox.getItems().setAll(lots);
            if (!lots.isEmpty()) {
                lotBox.setValue(lots.get(0));
            }
        }
        updateLoss();
    }

    private void updateLoss() {
        LotChoice choice = lotBox.getValue();
        if (choice == null) {
            lossLabel.setText("Estimated loss: -");
            return;
        }
        try {
            BigDecimal quantity = NumberParser.parseQuantity(quantityField.getText(), "Quantity");
            lossLabel.setText("Estimated loss: " + Formatters.money(
                    ctx.waste().previewLoss(choice.lot.getStockKind(), choice.lot.getId(), quantity)));
        } catch (ValidationException e) {
            lossLabel.setText("Estimated loss: -");
        }
    }

    @FXML
    private void onRecord() {
        LotChoice choice = lotBox.getValue();
        if (choice == null) {
            Dialogs.info(window(), "Record waste", "Choose an item and a lot or batch first.");
            return;
        }
        try {
            BigDecimal quantity = NumberParser.parseQuantity(quantityField.getText(), "Quantity");
            String item = itemBox.getValue().name;
            if (!Dialogs.confirm(window(), "Record waste", "Write off " + Formatters.quantity(quantity) + " of " + item
                    + " (" + reasonBox.getValue() + ")?\nEstimated loss: "
                    + Formatters.money(ctx.waste().previewLoss(choice.lot.getStockKind(), choice.lot.getId(), quantity)))) {
                return;
            }
            boolean ok = attempt(() -> {
                if (choice.lot instanceof IngredientLot) {
                    ctx.waste().recordIngredientWaste(choice.lot.getId(), quantity, reasonBox.getValue(), noteField.getText());
                } else {
                    ctx.waste().recordProductWaste(choice.lot.getId(), quantity, reasonBox.getValue(), noteField.getText());
                }
            });
            if (ok) {
                quantityField.clear();
                noteField.clear();
            }
        } catch (ValidationException e) {
            Dialogs.error(window(), e);
        }
    }

    @FXML
    private void onWriteOff() {
        List<FinishedGoodsBatch> selected = new ArrayList<>(endOfDayTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            selected.addAll(endOfDayTable.getItems());
        }
        if (selected.isEmpty()) {
            return;
        }
        BigDecimal loss = Money.ZERO;
        List<Long> ids = new ArrayList<>();
        for (FinishedGoodsBatch batch : selected) {
            ids.add(batch.getId());
            loss = loss.add(batch.getRemainingValue());
        }
        if (Dialogs.confirm(window(), "End-of-day write-off", "Write off " + ids.size() + " batch(es) in one step?\n"
                + "Estimated loss: " + Formatters.money(loss))) {
            attempt(() -> ctx.waste().writeOffEndOfDay(ids));
        }
    }
}
