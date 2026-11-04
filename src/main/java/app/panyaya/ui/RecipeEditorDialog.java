package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.recipe.RecipeItem;
import app.panyaya.service.StockQueries;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import app.panyaya.util.TextUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Recipe editor: basics, ingredient lines and a live cost estimate. "New product..." creates the
 * product on the spot, so a brand-new bread can be set up from this one dialog.
 */
final class RecipeEditorDialog {

    private RecipeEditorDialog() {
    }

    static Recipe show(Window owner, AppContext ctx, Recipe existing) {
        boolean editing = existing != null;
        FormDialog form = new FormDialog(owner, editing ? "Edit recipe" : "New recipe", "Save recipe");
        form.setWidth(760);

        TextField name = form.add("Recipe name *", UiUtil.field("e.g. Japanese Milk Bread", editing ? existing.getName() : ""));
        ComboBox<Product> product = new ComboBox<>();
        product.getItems().setAll(ctx.products().getActiveManufacturedProducts());
        product.setMaxWidth(Double.MAX_VALUE);
        Button newProduct = new Button("New product...");
        HBox productRow = new HBox(6, product, newProduct);
        HBox.setHgrow(product, Priority.ALWAYS);
        form.add("Makes product *", productRow);
        newProduct.setOnAction(e -> {
            Product created = ProductDialog.show(owner, ctx, null, ProductType.MANUFACTURED);
            if (created != null) {
                product.getItems().setAll(ctx.products().getActiveManufacturedProducts());
                product.setValue(ctx.products().getProduct(created.getId()));
                if (name.getText().isBlank()) {
                    name.setText(created.getName());
                }
            }
        });
        if (editing) {
            product.setValue(ctx.products().getProduct(existing.getProductId()));
        }

        TextField yield = form.add("Yield (pieces) *", UiUtil.field("pieces per batch, e.g. 12",
                editing ? Formatters.quantity(existing.getYieldQuantity()) : ""));
        TextField prep = form.add("Preparation (min)", UiUtil.field("0", editing ? String.valueOf(existing.getPrepMinutes()) : ""));
        TextField bake = form.add("Baking (min)", UiUtil.field("0", editing ? String.valueOf(existing.getBakeMinutes()) : ""));
        TextField shelfLife = form.add("Shelf life (h)", UiUtil.field("blank = product's shelf life",
                editing && existing.getShelfLifeHours() != null ? String.valueOf(existing.getShelfLifeHours()) : ""));
        TextArea instructions = form.add("Instructions", UiUtil.area("Steps for the baker", editing ? existing.getInstructions() : "", 3));

        // ---- ingredient lines ----
        Label itemsTitle = new Label("Ingredients for one batch");
        itemsTitle.getStyleClass().add("card-title");
        form.addWide(itemsTitle);

        ComboBox<Ingredient> ingredient = new ComboBox<>();
        ingredient.getItems().setAll(ctx.ingredients().getActiveIngredients());
        ingredient.setPromptText("Ingredient");
        ingredient.setPrefWidth(230);
        TextField quantity = UiUtil.field("Qty", "");
        quantity.setPrefWidth(80);
        ComboBox<Unit> unit = new ComboBox<>();
        unit.setPrefWidth(90);
        TextField note = UiUtil.field("Note (e.g. dough)", "");
        Button add = new Button("Add line");
        HBox entry = new HBox(6, ingredient, quantity, unit, note, add);
        HBox.setHgrow(note, Priority.ALWAYS);
        form.addWide(entry);
        // only units of the ingredient's dimension can be chosen (no mL for flour)
        ingredient.setOnAction(e -> {
            Ingredient chosen = ingredient.getValue();
            if (chosen != null) {
                unit.getItems().setAll(Unit.unitsOf(chosen.getBaseUnit().getDimension()));
                unit.setValue(chosen.getBaseUnit());
            }
        });

        ObservableList<RecipeItem> items = FXCollections.observableArrayList(
                editing ? existing.getItems() : new ArrayList<>());
        TableView<RecipeItem> itemTable = new TableView<>(items);
        itemTable.setPrefHeight(190);
        itemTable.getColumns().setAll(Arrays.asList(
                UiUtil.<RecipeItem>column("Ingredient", 260, i -> ingredientName(ctx, i.getIngredientId())),
                UiUtil.<RecipeItem>numberColumn("Quantity", 100, i -> Formatters.quantity(i.getQuantity())),
                UiUtil.<RecipeItem>column("Unit", 70, i -> i.getUnit().getSymbol()),
                UiUtil.<RecipeItem>column("Note", 220, RecipeItem::getNote)));
        UiUtil.placeholder(itemTable, "No ingredients yet. Choose an ingredient, a quantity and press \"Add line\".");
        form.addWide(itemTable);
        Button remove = new Button("Remove selected line");
        Label cost = new Label();
        cost.setWrapText(true);
        form.addWide(new HBox(12, remove, cost));
        remove.setOnAction(e -> {
            RecipeItem selected = itemTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                items.remove(selected);
                updateCost(ctx, items, yield.getText(), cost);
            }
        });
        add.setOnAction(e -> {
            try {
                if (ingredient.getValue() == null) {
                    throw new ValidationException("Choose an ingredient.");
                }
                items.add(new RecipeItem(ingredient.getValue().getId(),
                        NumberParser.parseQuantity(quantity.getText(), "Quantity"), unit.getValue(), note.getText()));
                quantity.clear();
                note.clear();
                ingredient.requestFocus();
                form.clearError();
                updateCost(ctx, items, yield.getText(), cost);
            } catch (ValidationException ex) {
                form.showError(ex.getMessage());
            }
        });
        yield.textProperty().addListener((obs, old, text) -> updateCost(ctx, items, text, cost));
        updateCost(ctx, items, yield.getText(), cost);
        form.addHint("Quantities are for one batch that yields the number of pieces above. "
                + "Editing an active recipe creates a new version; past production keeps the quantities it used.");

        final Recipe[] saved = new Recipe[1];
        boolean ok = form.show(() -> {
            if (product.getValue() == null) {
                throw new ValidationException("Choose the product this recipe makes (or create it with \"New product...\").");
            }
            BigDecimal yieldValue = NumberParser.parseQuantity(yield.getText(), "Yield");
            int prepMinutes = NumberParser.parseWholeNumber(prep.getText(), "Preparation", 0);
            int bakeMinutes = NumberParser.parseWholeNumber(bake.getText(), "Baking", 0);
            Integer shelf = TextUtil.isBlank(shelfLife.getText()) ? null
                    : NumberParser.parseWholeNumber(shelfLife.getText(), "Shelf life");
            List<RecipeItem> lines = new ArrayList<>(items);
            if (editing) {
                saved[0] = ctx.recipes().update(existing.getId(), name.getText(), product.getValue().getId(), yieldValue,
                        lines, prepMinutes, bakeMinutes, shelf, instructions.getText());
            } else {
                saved[0] = ctx.recipes().create(name.getText(), product.getValue().getId(), yieldValue, lines,
                        prepMinutes, bakeMinutes, shelf, instructions.getText());
            }
        });
        return ok ? saved[0] : null;
    }

    /** Live estimate: weighted average cost of usable lots, unknown costs are listed, never zero. */
    private static void updateCost(AppContext ctx, List<RecipeItem> items, String yieldText, Label label) {
        BakeryData data = ctx.getStore().getData();
        BigDecimal total = Money.ZERO;
        List<String> unknown = new ArrayList<>();
        for (RecipeItem item : items) {
            Ingredient ingredient = BakeryData.findById(data.getIngredients(), item.getIngredientId());
            if (ingredient == null || !ingredient.isStockTracked()) {
                continue;
            }
            BigDecimal unitCost = StockQueries.estimatedUnitCost(data, ingredient.getId(), ctx.getClock().instant(),
                    data.getSettings().zone());
            if (unitCost == null) {
                unknown.add(ingredient.getName());
            } else {
                total = total.add(Money.multiply(unitCost, item.quantityIn(ingredient.getBaseUnit())));
            }
        }
        String text = "Estimated material cost per batch: " + Formatters.money(total);
        try {
            BigDecimal pieces = NumberParser.parseQuantity(yieldText, "Yield");
            if (Quantities.isPositive(pieces)) {
                text += "  ·  per piece: " + Formatters.money(Money.divide(total, pieces));
            }
        } catch (ValidationException ignored) {
            // yield not typed yet
        }
        if (!unknown.isEmpty()) {
            text += "\n⚠ Cost unknown (never received): " + String.join(", ", unknown) + " - estimate incomplete.";
        }
        label.setText(text);
    }

    static String ingredientName(AppContext ctx, long ingredientId) {
        Ingredient ingredient = ctx.ingredients().getIngredient(ingredientId);
        return ingredient == null ? "?" : ingredient.getName();
    }
}
