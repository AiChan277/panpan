package app.panyaya.ui;

import app.panyaya.ui.common.Badge;
import app.panyaya.model.product.Product;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.recipe.RecipeItem;
import app.panyaya.model.recipe.RecipeStatus;
import app.panyaya.model.user.Permission;
import app.panyaya.service.CostEstimate;
import app.panyaya.service.MaterialRequirement;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.Arrays;

/** Recipes: list, details, cost estimate, scale calculator and all recipe actions. */
public class RecipesController extends BaseController {

    @FXML
    private TextField searchField;
    @FXML
    private CheckBox archivedCheck;
    @FXML
    private TableView<Recipe> table;
    @FXML
    private Label titleLabel;
    @FXML
    private Label infoLabel;
    @FXML
    private Label templateBanner;
    @FXML
    private TableView<RecipeItem> itemsTable;
    @FXML
    private Label costLabel;
    @FXML
    private TextField targetField;
    @FXML
    private Label scaleLabel;
    @FXML
    private TableView<MaterialRequirement> requirementsTable;
    @FXML
    private Label instructionsLabel;
    @FXML
    private Button newButton;
    @FXML
    private Button editButton;
    @FXML
    private Button duplicateButton;
    @FXML
    private Button activateButton;
    @FXML
    private Button archiveButton;
    @FXML
    private Button starterButton;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<Recipe>column("Recipe", 210, Recipe::getName),
                UiUtil.<Recipe>column("Product", 170, r -> productName(r.getProductId())),
                UiUtil.<Recipe>numberColumn("Yield", 60, r -> Formatters.quantity(r.getYieldQuantity())),
                UiUtil.<Recipe>badgeColumn("Status", 130, r -> UiUtil.statusText(r.getStatus().toString())
                        + (r.isTemplate() ? " (template)" : "")),
                UiUtil.<Recipe>numberColumn("Ver.", 50, r -> String.valueOf(r.getVersion()))));
        UiUtil.placeholder(table, "No recipes yet. Create one, or import the starter templates and adjust them.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, recipe) -> showDetails(recipe));
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && can(Permission.MANAGE_RECIPES)) {
                onEdit();
            }
        });
        itemsTable.getColumns().setAll(Arrays.asList(
                UiUtil.<RecipeItem>column("Ingredient", 220, i -> RecipeEditorDialog.ingredientName(ctx, i.getIngredientId())),
                UiUtil.<RecipeItem>numberColumn("Quantity", 90, i -> Formatters.quantity(i.getQuantity())),
                UiUtil.<RecipeItem>column("Unit", 60, i -> i.getUnit().getSymbol()),
                UiUtil.<RecipeItem>column("Note", 160, RecipeItem::getNote)));
        UiUtil.placeholder(itemsTable, "Select a recipe to see its ingredients.");
        requirementsTable.getColumns().setAll(Arrays.asList(
                UiUtil.<MaterialRequirement>column("Ingredient", 190, MaterialRequirement::getIngredientName),
                UiUtil.<MaterialRequirement>numberColumn("Required", 110,
                        r -> Formatters.quantity(r.getRequired(), r.getUnit())),
                UiUtil.<MaterialRequirement>numberColumn("In stock", 110, r -> r.isStockTracked()
                        ? Formatters.quantity(r.getAvailable(), r.getUnit()) : "not tracked"),
                UiUtil.highlight(UiUtil.<MaterialRequirement>numberColumn("Short", 100, r -> r.isShort()
                        ? Formatters.quantity(r.getShortfall(), r.getUnit()) : "-"), value -> !"-".equals(value), "text-danger"),
                UiUtil.<MaterialRequirement>numberColumn("Est. cost", 100, r -> r.getEstimatedCost() == null
                        ? "unknown" : Formatters.money(r.getEstimatedCost()))));
        UiUtil.placeholder(requirementsTable, "Enter a number of pieces and press Calculate.");
        searchField.textProperty().addListener((obs, old, text) -> refresh());
        archivedCheck.setOnAction(e -> refresh());

        boolean manage = can(Permission.MANAGE_RECIPES);
        newButton.setDisable(!manage);
        editButton.setDisable(!manage);
        duplicateButton.setDisable(!manage);
        archiveButton.setDisable(!manage);
        activateButton.setDisable(!can(Permission.MANAGE_PRODUCTS)); // owner releases recipes
        starterButton.setDisable(!can(Permission.MANAGE_SETTINGS));
    }

    @Override
    public void refresh() {
        UiUtil.setItems(table, ctx.recipes().search(searchField.getText(), archivedCheck.isSelected()));
        starterButton.setVisible(!ctx.seed().isStarterImported());
        starterButton.setManaged(!ctx.seed().isStarterImported());
        showDetails(table.getSelectionModel().getSelectedItem());
    }

    private void showDetails(Recipe recipe) {
        requirementsTable.getItems().clear();
        UiUtil.notice(scaleLabel, Badge.Tone.INFO, "");
        if (recipe == null) {
            titleLabel.setText("Select a recipe");
            infoLabel.setText("");
            UiUtil.notice(costLabel, Badge.Tone.INFO, "");
            instructionsLabel.setText("");
            itemsTable.getItems().clear();
            templateBanner.setVisible(false);
            templateBanner.setManaged(false);
            return;
        }
        titleLabel.setText(recipe.getName() + "  ·  version " + recipe.getVersion());
        infoLabel.setText("Makes " + Formatters.quantity(recipe.getYieldQuantity()) + " x " + productName(recipe.getProductId())
                + "  ·  " + recipe.getStatus() + "  ·  prep " + recipe.getPrepMinutes() + " min, bake "
                + recipe.getBakeMinutes() + " min  ·  updated "
                + Formatters.dateTime(recipe.getUpdatedAt(), ctx.settings().getSettings().zone()));
        templateBanner.setVisible(recipe.isTemplate());
        templateBanner.setManaged(recipe.isTemplate());
        itemsTable.getItems().setAll(recipe.getItems());
        CostEstimate estimate = ctx.recipes().estimateCost(recipe);
        String cost = "Estimated material cost: " + Formatters.money(estimate.getBatchCost()) + " per batch, "
                + Formatters.money(estimate.getCostPerUnit()) + " per piece";
        Product product = ctx.products().getProduct(recipe.getProductId());
        if (product != null) {
            cost += " (selling price " + Formatters.money(product.getSellingPrice()) + ")";
        }
        if (!estimate.isComplete()) {
            cost += "\nCost unknown for: " + String.join(", ", estimate.getUnknownCostIngredients())
                    + " (never received) - the estimate is incomplete.";
        }
        UiUtil.notice(costLabel, estimate.isComplete() ? Badge.Tone.INFO : Badge.Tone.WARNING, cost);
        instructionsLabel.setText(recipe.getInstructions() == null || recipe.getInstructions().isEmpty()
                ? "-" : recipe.getInstructions());
    }

    private String productName(long productId) {
        Product product = ctx.products().getProduct(productId);
        return product == null ? "?" : product.getName();
    }

    private Recipe selected(String action) {
        Recipe recipe = table.getSelectionModel().getSelectedItem();
        if (recipe == null) {
            Dialogs.info(window(), action, "Select a recipe first.");
        }
        return recipe;
    }

    @FXML
    private void onCalculate() {
        Recipe recipe = selected("Scale calculator");
        if (recipe == null) {
            return;
        }
        try {
            java.math.BigDecimal target = NumberParser.parseQuantity(targetField.getText(), "Pieces");
            requirementsTable.getItems().setAll(ctx.recipes().calculateRequirements(recipe, target));
            long shortCount = requirementsTable.getItems().stream().filter(MaterialRequirement::isShort).count();
            if (shortCount == 0) {
                UiUtil.notice(scaleLabel, Badge.Tone.SUCCESS, "Enough stock for " + Formatters.quantity(target) + " pieces.");
            } else {
                UiUtil.notice(scaleLabel, Badge.Tone.WARNING, shortCount + " ingredient(s) short for "
                        + Formatters.quantity(target) + " pieces.");
            }
        } catch (Exception e) {
            Dialogs.error(window(), e);
        }
    }

    @FXML
    private void onNew() {
        Recipe created = RecipeEditorDialog.show(window(), ctx, null);
        if (created != null) {
            refresh();
            table.getSelectionModel().select(created);
        }
    }

    @FXML
    private void onEdit() {
        Recipe recipe = selected("Edit recipe");
        if (recipe != null && RecipeEditorDialog.show(window(), ctx, recipe) != null) {
            refresh();
        }
    }

    @FXML
    private void onDuplicate() {
        Recipe recipe = selected("Duplicate recipe");
        if (recipe == null) {
            return;
        }
        FormDialog form = new FormDialog(window(), "Duplicate recipe", "Duplicate");
        TextField name = form.add("New recipe name", UiUtil.field("", recipe.getName() + " (copy)"));
        ComboBox<Product> product = new ComboBox<>();
        product.getItems().setAll(ctx.products().getActiveManufacturedProducts());
        product.setValue(ctx.products().getProduct(recipe.getProductId()));
        product.setMaxWidth(Double.MAX_VALUE);
        form.add("For product", product);
        form.addHint("The copy starts as a Draft. Use it to make a variation or a recipe for another product.");
        String resaleNote = RecipeEditorDialog.resaleProductsNote(ctx);
        if (resaleNote != null) {
            form.addHint(resaleNote);
        }
        if (form.show(() -> ctx.recipes().duplicate(recipe.getId(), name.getText(),
                product.getValue() == null ? recipe.getProductId() : product.getValue().getId()))) {
            refresh();
        }
    }

    @FXML
    private void onActivate() {
        Recipe recipe = selected("Activate recipe");
        if (recipe == null) {
            return;
        }
        if (recipe.getStatus() == RecipeStatus.ACTIVE) {
            Dialogs.info(window(), "Activate recipe", "This recipe is already active.");
            return;
        }
        boolean reviewed = false;
        if (recipe.isTemplate()) {
            reviewed = Dialogs.confirm(window(), "Activate starter template",
                    "This recipe came from the starter templates and contains EXAMPLE quantities.\n\n"
                            + "Confirm: I have checked the quantities of this recipe.");
            if (!reviewed) {
                return;
            }
        }
        final boolean confirmed = reviewed;
        attempt(() -> ctx.recipes().activate(recipe.getId(), confirmed));
    }

    @FXML
    private void onArchive() {
        Recipe recipe = selected("Archive recipe");
        if (recipe != null && Dialogs.confirmDanger(window(), "Archive recipe",
                "Archive \"" + recipe.getName() + "\"? It can no longer be used for new production.", "Archive")) {
            attempt(() -> ctx.recipes().archive(recipe.getId()));
        }
    }

    @FXML
    private void onImportStarter() {
        if (Dialogs.confirm(window(), "Starter templates",
                "Import 4 example recipes (white bread, chocolate bun, donut, croissant) with their ingredients and "
                        + "products? Existing items with the same code or SKU are not changed. The recipes are Drafts "
                        + "with example quantities that you must review.")) {
            attempt(() -> {
                int count = ctx.seed().importStarterRecipes();
                Dialogs.success(window(), "Starter templates", count + " template recipe(s) imported as Draft.");
            });
        }
    }
}
