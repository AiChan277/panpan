package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.model.StockKind;
import app.panyaya.model.inventory.AdjustmentReason;
import app.panyaya.model.inventory.StockLot;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import java.util.Arrays;

/** Owner-only stock correction for one lot or batch, with a mandatory reason. */
final class AdjustStockDialog {

    private AdjustStockDialog() {
    }

    static boolean show(Window owner, AppContext ctx, StockKind kind, StockLot lot) {
        FormDialog form = new FormDialog(owner, "Adjust stock", "Save adjustment");
        String item = ctx.inventory().itemName(kind, lot.getItemId());
        String unit = ctx.inventory().unitOf(kind, lot.getItemId()).getSymbol();
        form.add("Item", new Label(item));
        form.add("Lot / batch", new Label(lot.getLotCode() + "  (currently " + Formatters.quantity(lot.getRemainingQuantity())
                + " " + unit + ")"));
        ComboBox<String> direction = new ComboBox<>();
        direction.getItems().setAll(Arrays.asList("Decrease (counted less)", "Increase (counted more)"));
        direction.setValue(direction.getItems().get(0));
        form.add("Direction *", direction);
        TextField quantity = form.add("Quantity (" + unit + ") *", UiUtil.field("e.g. 120", ""));
        ComboBox<AdjustmentReason> reason = new ComboBox<>();
        reason.getItems().setAll(AdjustmentReason.values());
        reason.setValue(AdjustmentReason.COUNT_CORRECTION);
        form.add("Reason *", reason);
        TextField note = form.add("Note *", UiUtil.field("e.g. physical count 4 Nov", ""));
        form.addHint("Adjustments never edit the stock directly: they add a ledger entry, so the change stays "
                + "visible and attributable. Stock can never go below zero.");
        return form.show(() -> ctx.inventory().adjustStock(kind, lot.getId(),
                direction.getValue().startsWith("Increase"), NumberParser.parseQuantity(quantity.getText(), "Quantity"),
                reason.getValue(), note.getText()));
    }
}
