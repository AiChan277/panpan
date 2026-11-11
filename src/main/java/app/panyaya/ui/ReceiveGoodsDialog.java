package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.product.Product;
import app.panyaya.model.supplier.GoodsReceipt;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.service.ReceiptLineInput;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * "Receive goods": one delivery with several lines. Each line becomes a lot (ingredients) or a batch
 * (resale products) with its own cost and expiry. The whole delivery is saved in one operation.
 */
final class ReceiveGoodsDialog {

    /** Something that can be received: an ingredient or a resale product. */
    private static class Receivable {
        final StockKind kind;
        final long id;
        final String label;
        final Unit baseUnit;
        final boolean needsExpiry;

        Receivable(StockKind kind, long id, String label, Unit baseUnit, boolean needsExpiry) {
            this.kind = kind;
            this.id = id;
            this.label = label;
            this.baseUnit = baseUnit;
            this.needsExpiry = needsExpiry;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private ReceiveGoodsDialog() {
    }

    static boolean show(Window owner, AppContext ctx) {
        FormDialog form = new FormDialog(owner, "Receive goods", "Save delivery");
        form.setWidth(880);

        ComboBox<Supplier> supplier = new ComboBox<>();
        supplier.getItems().setAll(ctx.suppliers().getActiveSuppliers());
        supplier.setMaxWidth(Double.MAX_VALUE);
        if (!supplier.getItems().isEmpty()) {
            supplier.setValue(supplier.getItems().get(0));
        }
        form.add("Supplier *", supplier);
        DatePicker received = form.add("Received on *", new DatePicker(LocalDate.ofInstant(ctx.getClock().instant(),
                ctx.settings().getSettings().zone())));
        TextField invoice = form.add("Invoice / note no.", UiUtil.field("optional", ""));

        Label linesTitle = new Label("Received items");
        linesTitle.getStyleClass().add("card-title");
        form.addWide(linesTitle);

        ComboBox<Receivable> item = new ComboBox<>();
        for (Ingredient ingredient : ctx.ingredients().getActiveIngredients()) {
            if (ingredient.isStockTracked()) {
                item.getItems().add(new Receivable(StockKind.INGREDIENT, ingredient.getId(), ingredient.getName(),
                        ingredient.getBaseUnit(), ingredient.isTracksExpiry()));
            }
        }
        for (Product product : ctx.products().getActiveResaleProducts()) {
            item.getItems().add(new Receivable(StockKind.PRODUCT, product.getId(), product.getName() + " (resale)",
                    Unit.PCS, true));
        }
        item.setPromptText("Ingredient or resale product");
        item.setPrefWidth(230);
        TextField quantity = UiUtil.field("Qty", "");
        quantity.setPrefWidth(70);
        ComboBox<Unit> unit = new ComboBox<>();
        unit.setPrefWidth(85);
        TextField cost = UiUtil.field("Total Rp", "");
        cost.setPrefWidth(100);
        TextField lot = UiUtil.field("Lot code (auto)", "");
        lot.setPrefWidth(110);
        DatePicker expiry = new DatePicker();
        expiry.setPromptText("Expiry");
        expiry.setPrefWidth(130);
        Button add = new Button("Add");
        HBox entry = new HBox(6, item, quantity, unit, cost, lot, expiry, add);
        HBox.setHgrow(item, Priority.ALWAYS);
        form.addWide(entry);
        item.setOnAction(e -> {
            Receivable chosen = item.getValue();
            if (chosen != null) {
                unit.getItems().setAll(Unit.unitsOf(chosen.baseUnit.getDimension()));
                unit.setValue(chosen.baseUnit == Unit.G ? Unit.KG : chosen.baseUnit);
                expiry.setPromptText(chosen.needsExpiry ? "Expiry *" : "Expiry");
            }
        });

        ObservableList<ReceiptLineInput> lines = FXCollections.observableArrayList();
        ObservableList<String> labels = FXCollections.observableArrayList();
        TableView<ReceiptLineInput> table = new TableView<>(lines);
        table.setPrefHeight(200);
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<ReceiptLineInput>column("Item", 240, l -> labels.get(lines.indexOf(l))),
                UiUtil.<ReceiptLineInput>numberColumn("Quantity", 110, l -> Formatters.quantity(l.getQuantity(), l.getUnit())),
                UiUtil.<ReceiptLineInput>numberColumn("Total cost", 120, l -> Formatters.money(l.getTotalCost())),
                UiUtil.<ReceiptLineInput>column("Lot", 120, l -> l.getLotCode().isEmpty() ? "(auto)" : l.getLotCode()),
                UiUtil.<ReceiptLineInput>column("Expiry", 110, l -> Formatters.date(l.getExpiryDate()))));
        UiUtil.placeholder(table, "Add each delivered item: quantity, unit, total price paid and expiry date.");
        form.addWide(table);
        Button remove = new Button("Remove selected line");
        Label total = new Label("Total: Rp 0");
        total.getStyleClass().add("card-title");
        form.addWide(new HBox(12, remove, total));
        form.addHint("Example: 25 kg flour for Rp 312.500 becomes a lot of 25,000 g at Rp 12,50 per gram. "
                + "Quantities accept 1.5 or 1,5; amounts accept 312500 or 312.500.");

        remove.setOnAction(e -> {
            int index = table.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                labels.remove(index);
                lines.remove(index);
                total.setText("Total: " + Formatters.money(sum(lines)));
            }
        });
        add.setOnAction(e -> {
            try {
                Receivable chosen = item.getValue();
                if (chosen == null) {
                    throw new ValidationException("Choose an item.");
                }
                ReceiptLineInput line = new ReceiptLineInput(chosen.kind, chosen.id,
                        NumberParser.parseQuantity(quantity.getText(), "Quantity"), unit.getValue(),
                        NumberParser.parseMoney(cost.getText(), "Total cost"), lot.getText().trim(), expiry.getValue());
                if (chosen.needsExpiry && line.getExpiryDate() == null) {
                    throw new ValidationException(chosen.label + " needs an expiry date.");
                }
                labels.add(chosen.label);
                lines.add(line);
                total.setText("Total: " + Formatters.money(sum(lines)));
                quantity.clear();
                cost.clear();
                lot.clear();
                expiry.setValue(null);
                form.clearError();
                item.requestFocus();
            } catch (ValidationException ex) {
                form.showError(ex.getMessage());
            }
        });

        final GoodsReceipt[] saved = new GoodsReceipt[1];
        boolean ok = form.show(() -> {
            if (supplier.getValue() == null) {
                throw new ValidationException("Choose a supplier (use \"Market / Cash\" for market purchases).");
            }
            saved[0] = ctx.inventory().receiveGoods(supplier.getValue().getId(), received.getValue(), invoice.getText(),
                    new ArrayList<>(lines));
        });
        if (ok) {
            Dialogs.success(owner, "Goods received", saved[0].getReceiptNo() + " saved: " + saved[0].getLines().size()
                    + " line(s), total " + Formatters.money(saved[0].getTotalCost()) + ".");
        }
        return ok;
    }

    private static java.math.BigDecimal sum(ObservableList<ReceiptLineInput> lines) {
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (ReceiptLineInput line : lines) {
            total = total.add(line.getTotalCost());
        }
        return total;
    }
}
