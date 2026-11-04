package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import app.panyaya.util.TextUtil;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import java.math.BigDecimal;

/** Create / edit ingredient form. A new ingredient can start with an opening stock. */
final class IngredientDialog {

    private IngredientDialog() {
    }

    static Ingredient show(Window owner, AppContext ctx, Ingredient existing) {
        boolean editing = existing != null;
        FormDialog form = new FormDialog(owner, editing ? "Edit ingredient" : "New ingredient", "Save ingredient");

        TextField name = form.add("Name *", UiUtil.field("e.g. Bread flour (high protein)", editing ? existing.getName() : ""));
        TextField code = form.add("Code *", UiUtil.field("e.g. FLOUR-BREAD", editing ? existing.getCode() : ""));
        ComboBox<Unit> unit = new ComboBox<>();
        unit.getItems().setAll(Unit.baseUnits());
        unit.setValue(editing ? existing.getBaseUnit() : Unit.G);
        unit.setDisable(editing); // the stock unit cannot change once lots exist
        form.add("Stock unit *", unit);
        TextField minimum = form.add("Minimum stock *", UiUtil.field("in the stock unit, e.g. 5000",
                editing ? Formatters.quantity(existing.getMinimumStock()) : "0"));
        CheckBox tracksExpiry = form.add("Expiry", new CheckBox("Lots have an expiry date"));
        tracksExpiry.setSelected(!editing || existing.isTracksExpiry());
        CheckBox stockTracked = form.add("Stock", new CheckBox("Count this ingredient in stock (untick for tap water)"));
        stockTracked.setSelected(!editing || existing.isStockTracked());
        ComboBox<Supplier> supplier = new ComboBox<>();
        supplier.getItems().add(null);
        supplier.getItems().addAll(ctx.suppliers().getActiveSuppliers());
        supplier.setMaxWidth(Double.MAX_VALUE);
        supplier.setPromptText("optional");
        if (editing && existing.getDefaultSupplierId() != null) {
            supplier.setValue(ctx.suppliers().getSupplier(existing.getDefaultSupplierId()));
        }
        form.add("Default supplier", supplier);
        TextField notes = form.add("Notes", UiUtil.field("optional", editing ? existing.getNotes() : ""));

        TextField openingQty = null;
        TextField openingCost = null;
        DatePicker openingExpiry = null;
        if (!editing) {
            Label section = new Label("Opening stock (optional) - what is in the store room today");
            section.getStyleClass().add("card-title");
            form.addWide(section);
            openingQty = form.add("Quantity", UiUtil.field("in the stock unit, e.g. 2500", ""));
            openingCost = form.add("Total value (Rp)", UiUtil.field("what this stock cost, e.g. 31250", ""));
            openingExpiry = form.add("Expiry date", new DatePicker());
            form.addHint("Opening stock becomes a normal lot with a ledger entry. Later deliveries are recorded "
                    + "with \"Receive goods\".");
        }

        final TextField qtyField = openingQty;
        final TextField costField = openingCost;
        final DatePicker expiryField = openingExpiry;
        final Ingredient[] saved = new Ingredient[1];
        boolean ok = form.show(() -> {
            BigDecimal minimumValue = NumberParser.parseQuantity(minimum.getText(), "Minimum stock");
            Long supplierId = supplier.getValue() == null ? null : supplier.getValue().getId();
            if (editing) {
                saved[0] = ctx.ingredients().update(existing.getId(), code.getText(), name.getText(), minimumValue,
                        tracksExpiry.isSelected(), stockTracked.isSelected(), supplierId, notes.getText());
            } else {
                BigDecimal qty = TextUtil.isBlank(qtyField.getText()) ? null
                        : NumberParser.parseQuantity(qtyField.getText(), "Opening quantity");
                BigDecimal cost = TextUtil.isBlank(costField.getText()) ? null
                        : NumberParser.parseMoney(costField.getText(), "Opening value");
                saved[0] = ctx.ingredients().create(code.getText(), name.getText(), unit.getValue(), minimumValue,
                        tracksExpiry.isSelected(), stockTracked.isSelected(), supplierId, notes.getText(), qty, cost,
                        expiryField.getValue());
            }
        });
        return ok ? saved[0] : null;
    }
}
