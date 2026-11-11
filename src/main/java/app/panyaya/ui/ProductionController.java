package app.panyaya.ui;

import app.panyaya.ui.common.Badge;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.Quantities;
import app.panyaya.model.product.Product;
import app.panyaya.model.production.MaterialUsage;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.production.ProductionStatus;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.service.MaterialRequirement;
import app.panyaya.service.RecipeService;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Production planning and execution. */
public class ProductionController extends BaseController {

    /** One row of the detail table: either a requirement (planned) or a used material (started). */
    private static class DetailRow {
        final String ingredient;
        final String required;
        final String available;
        final String status;
        final String cost;

        DetailRow(String ingredient, String required, String available, String status, String cost) {
            this.ingredient = ingredient;
            this.required = required;
            this.available = available;
            this.status = status;
            this.cost = cost;
        }
    }

    @FXML
    private ComboBox<ProductionStatus> statusFilter;
    @FXML
    private TableView<ProductionOrder> table;
    @FXML
    private Label detailTitle;
    @FXML
    private Label detailInfo;
    @FXML
    private TableView<DetailRow> detailTable;
    @FXML
    private Button startButton;
    @FXML
    private Button completeButton;
    @FXML
    private Button failButton;
    @FXML
    private Button cancelButton;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<ProductionOrder>column("Order", 150, ProductionOrder::getOrderNo),
                UiUtil.<ProductionOrder>column("Planned for", 110, o -> Formatters.date(o.getPlannedDate())),
                UiUtil.<ProductionOrder>column("Product", 190, ProductionOrder::getProductName),
                UiUtil.<ProductionOrder>column("Recipe", 170, o -> o.getRecipeName() + " v" + o.getRecipeVersion()),
                UiUtil.<ProductionOrder>numberColumn("Planned", 70, o -> String.valueOf(o.getPlannedQuantity())),
                UiUtil.<ProductionOrder>numberColumn("Good", 60, o -> o.getStatus() == ProductionStatus.COMPLETED
                        ? String.valueOf(o.getGoodQuantity()) : "-"),
                UiUtil.<ProductionOrder>numberColumn("Rejected", 70, o -> o.getStatus() == ProductionStatus.COMPLETED
                        ? String.valueOf(o.getRejectedQuantity()) : "-"),
                UiUtil.<ProductionOrder>badgeColumn("Status", 120, o -> UiUtil.statusText(o.getStatus().toString())),
                UiUtil.<ProductionOrder>numberColumn("Material cost", 110, o -> o.getStatus() == ProductionStatus.PLANNED
                        || o.getStatus() == ProductionStatus.CANCELLED ? "-" : Formatters.money(o.getMaterialCost())),
                UiUtil.<ProductionOrder>numberColumn("Cost / pc", 90, o -> o.getStatus() == ProductionStatus.COMPLETED
                        ? Formatters.money(o.getUnitCost()) : "-")));
        UiUtil.placeholder(table, "No production orders. Click \"Plan production\" to plan a bake.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, order) -> showDetail(order));

        detailTable.getColumns().setAll(Arrays.asList(
                UiUtil.<DetailRow>column("Ingredient", 220, r -> r.ingredient),
                UiUtil.<DetailRow>numberColumn("Quantity", 120, r -> r.required),
                UiUtil.<DetailRow>numberColumn("Usable in stock", 130, r -> r.available),
                UiUtil.<DetailRow>badgeColumn("Check", 200, r -> r.status),
                UiUtil.<DetailRow>numberColumn("Cost", 110, r -> r.cost)));

        UiUtil.placeholder(detailTable, "Select an order to see the ingredients it needs or used.");
        UiUtil.allOption(statusFilter, "All statuses", Arrays.asList(ProductionStatus.values()));
        statusFilter.setOnAction(e -> refresh());
    }

    @Override
    public void refresh() {
        UiUtil.setItems(table, ctx.production().search(null, null, null, statusFilter.getValue()));
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    private void showDetail(ProductionOrder order) {
        ProductionStatus status = order == null ? null : order.getStatus();
        startButton.setDisable(status != ProductionStatus.PLANNED);
        cancelButton.setDisable(status != ProductionStatus.PLANNED);
        completeButton.setDisable(status != ProductionStatus.IN_PROGRESS);
        failButton.setDisable(status != ProductionStatus.IN_PROGRESS);
        List<DetailRow> rows = new ArrayList<>();
        if (order == null) {
            detailTitle.setText("Select an order to see its ingredients");
            detailInfo.setText("");
            detailTable.getItems().clear();
            return;
        }
        if (status == ProductionStatus.PLANNED) {
            detailTitle.setText("Ingredients needed for " + order.getPlannedQuantity() + " x " + order.getProductName());
            detailInfo.setText("Planning does not use stock. Stock is checked again and used when you press Start.");
            try {
                for (MaterialRequirement r : ctx.production().requirementsFor(order.getId())) {
                    rows.add(requirementRow(r));
                }
            } catch (ValidationException e) {
                detailInfo.setText(e.getMessage());
            }
        } else {
            detailTitle.setText("Ingredients used by " + order.getOrderNo());
            String info = "Status: " + order.getStatus();
            if (order.getStartedAt() != null) {
                info += " · started " + Formatters.dateTime(order.getStartedAt(), ctx.settings().getSettings().zone());
            }
            if (order.getFinishedAt() != null) {
                info += " · finished " + Formatters.dateTime(order.getFinishedAt(), ctx.settings().getSettings().zone());
            }
            if (order.getNote() != null && !order.getNote().isEmpty()) {
                info += " · note: " + order.getNote();
            }
            detailInfo.setText(info);
            for (MaterialUsage usage : order.getMaterials()) {
                rows.add(new DetailRow(usage.getIngredientName(), Formatters.quantity(usage.getQuantity(), usage.getUnit()),
                        "", usage.isStockTracked() ? "Used (FEFO)" : "Not stock-tracked",
                        Formatters.money(usage.getCost())));
            }
        }
        detailTable.getItems().setAll(rows);
    }

    private static DetailRow requirementRow(MaterialRequirement r) {
        String check;
        if (!r.isStockTracked()) {
            check = "Not stock-tracked";
        } else if (r.isShort()) {
            check = "Short by " + Formatters.quantity(r.getShortfall(), r.getUnit());
        } else {
            check = "Enough";
        }
        return new DetailRow(r.getIngredientName(), Formatters.quantity(r.getRequired(), r.getUnit()),
                r.isStockTracked() ? Formatters.quantity(r.getAvailable(), r.getUnit()) : "-", check,
                r.getEstimatedCost() == null ? "unknown" : Formatters.money(r.getEstimatedCost()));
    }

    private ProductionOrder selected() {
        ProductionOrder order = table.getSelectionModel().getSelectedItem();
        if (order == null) {
            Dialogs.info(window(), "Production", "Select a production order first.");
        }
        return order;
    }

    @FXML
    private void onPlan() {
        FormDialog form = new FormDialog(window(), "Plan production", "Save plan");
        form.setWidth(720);
        ComboBox<Product> product = new ComboBox<>();
        product.getItems().setAll(ctx.products().getActiveManufacturedProducts());
        product.setMaxWidth(Double.MAX_VALUE);
        form.add("Product *", product);
        ComboBox<Recipe> recipe = new ComboBox<>();
        recipe.setMaxWidth(Double.MAX_VALUE);
        form.add("Recipe *", recipe);
        TextField quantity = form.add("Quantity (pieces) *", UiUtil.field("e.g. 48", ""));
        DatePicker date = form.add("Bake on *", new DatePicker(LocalDate.ofInstant(ctx.getClock().instant(),
                ctx.settings().getSettings().zone())));
        Label check = new Label();
        UiUtil.notice(check, Badge.Tone.INFO, "");
        form.addWide(check);
        TableView<DetailRow> preview = new TableView<>();
        preview.getColumns().setAll(Arrays.asList(
                UiUtil.<DetailRow>column("Ingredient", 200, r -> r.ingredient),
                UiUtil.<DetailRow>numberColumn("Needed", 110, r -> r.required),
                UiUtil.<DetailRow>numberColumn("Usable", 110, r -> r.available),
                UiUtil.<DetailRow>badgeColumn("Check", 170, r -> r.status)));
        preview.setPrefHeight(180);
        UiUtil.placeholder(preview, "Choose a product, an active recipe and a quantity.");
        form.addWide(preview);

        Runnable updatePreview = () -> {
            preview.getItems().clear();
            UiUtil.notice(check, Badge.Tone.INFO, "");
            if (recipe.getValue() == null) {
                return;
            }
            try {
                int pieces = NumberParser.parseWholeNumber(quantity.getText(), "Quantity");
                List<MaterialRequirement> requirements = RecipeService.calculateRequirements(ctx.getStore().getData(),
                        recipe.getValue(), Quantities.of(pieces), ctx.getClock().instant(), ctx.settings().getSettings().zone());
                long shortCount = 0;
                for (MaterialRequirement r : requirements) {
                    preview.getItems().add(requirementRow(r));
                    if (r.isShort()) {
                        shortCount++;
                    }
                }
                if (shortCount == 0) {
                    UiUtil.notice(check, Badge.Tone.SUCCESS, "Enough stock right now.");
                } else {
                    UiUtil.notice(check, Badge.Tone.WARNING, shortCount + " ingredient(s) short today. You can still "
                            + "plan; Start will be refused until the stock is received.");
                }
            } catch (ValidationException ignored) {
                // quantity not typed yet
            }
        };
        product.setOnAction(e -> {
            recipe.getItems().clear();
            if (product.getValue() != null) {
                recipe.getItems().setAll(ctx.recipes().getActiveRecipesFor(product.getValue().getId()));
                if (!recipe.getItems().isEmpty()) {
                    recipe.setValue(recipe.getItems().get(0));
                } else {
                    UiUtil.notice(check, Badge.Tone.WARNING, "\"" + product.getValue().getName() + "\" has no ACTIVE "
                            + "recipe. Create or activate one on the Recipes screen first.");
                }
            }
            updatePreview.run();
        });
        recipe.setOnAction(e -> updatePreview.run());
        quantity.textProperty().addListener((obs, old, text) -> updatePreview.run());

        boolean ok = form.show(() -> {
            if (product.getValue() == null || recipe.getValue() == null) {
                throw new ValidationException("Choose a product and one of its active recipes.");
            }
            ctx.production().plan(product.getValue().getId(), recipe.getValue().getId(),
                    NumberParser.parseWholeNumber(quantity.getText(), "Quantity"), date.getValue());
        });
        if (ok) {
            refresh();
        }
    }

    @FXML
    private void onStart() {
        ProductionOrder order = selected();
        if (order != null && Dialogs.confirm(window(), "Start production",
                "Start " + order.getOrderNo() + " (" + order.getPlannedQuantity() + " x " + order.getProductName()
                        + ")?\nAll ingredients are taken from stock now (earliest expiry first).")) {
            attempt(() -> {
                ProductionOrder started = ctx.production().start(order.getId());
                shell.setStatus("Started " + started.getOrderNo() + " - material cost " + Formatters.money(started.getMaterialCost()));
            });
        }
    }

    @FXML
    private void onComplete() {
        ProductionOrder order = selected();
        if (order == null) {
            return;
        }
        FormDialog form = new FormDialog(window(), "Complete production", "Complete");
        form.addHint(order.getOrderNo() + ": planned " + order.getPlannedQuantity() + " x " + order.getProductName() + ".");
        TextField good = form.add("Good pieces *", UiUtil.field("ready to sell", String.valueOf(order.getPlannedQuantity())));
        TextField rejected = form.add("Rejected pieces", UiUtil.field("burnt, misshapen...", "0"));
        form.addHint("Good pieces become a sellable batch with expiry = now + shelf life. Rejected pieces are "
                + "recorded as production-failure waste. If nothing is usable, use \"Mark failed\".");
        if (form.show(() -> {
            int goodCount = NumberParser.parseWholeNumber(good.getText(), "Good pieces");
            int rejectedCount = NumberParser.parseWholeNumber(rejected.getText(), "Rejected pieces", 0);
            int difference = Math.abs(goodCount + rejectedCount - order.getPlannedQuantity());
            if (difference * 5 > order.getPlannedQuantity()
                    && !Dialogs.confirm(window(), "Check quantities", "Good + rejected (" + (goodCount + rejectedCount)
                    + ") differs from the plan (" + order.getPlannedQuantity() + ") by more than 20 %. Continue?")) {
                throw new ValidationException("Please check the quantities.");
            }
            ctx.production().complete(order.getId(), goodCount, rejectedCount);
        })) {
            refresh();
        }
    }

    @FXML
    private void onFail() {
        ProductionOrder order = selected();
        if (order == null) {
            return;
        }
        String reason = Dialogs.ask(window(), "Mark batch as failed",
                "What went wrong? The used ingredients are recorded as a production-failure loss.", "");
        if (reason != null) {
            attempt(() -> ctx.production().markFailed(order.getId(), reason));
        }
    }

    @FXML
    private void onCancel() {
        ProductionOrder order = selected();
        if (order == null) {
            return;
        }
        String reason = Dialogs.ask(window(), "Cancel plan", "Reason for cancelling " + order.getOrderNo() + ":", "");
        if (reason != null) {
            attempt(() -> ctx.production().cancel(order.getId(), reason));
        }
    }
}
