package app.panyaya.ui;

import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.user.Permission;
import app.panyaya.service.IngredientStock;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** Ingredients: master data with live stock status and the lots of the selected ingredient. */
public class IngredientsController extends BaseController {

    @FXML
    private TextField searchField;
    @FXML
    private CheckBox inactiveCheck;
    @FXML
    private Label summaryLabel;
    @FXML
    private TableView<IngredientStock> table;
    @FXML
    private Label lotsTitle;
    @FXML
    private TableView<IngredientLot> lotsTable;
    @FXML
    private Button newButton;
    @FXML
    private Button editButton;
    @FXML
    private Button toggleButton;
    @FXML
    private Button receiveButton;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<IngredientStock>column("Ingredient", 230, s -> s.getIngredient().getName()
                        + (s.getIngredient().isActive() ? "" : " (inactive)")),
                UiUtil.<IngredientStock>column("Code", 120, s -> s.getIngredient().getCode()),
                UiUtil.<IngredientStock>numberColumn("Usable stock", 120,
                        s -> Formatters.quantity(s.getUsableQuantity(), s.getIngredient().getBaseUnit())),
                UiUtil.<IngredientStock>numberColumn("Minimum", 100,
                        s -> Formatters.quantity(s.getIngredient().getMinimumStock(), s.getIngredient().getBaseUnit())),
                UiUtil.<IngredientStock>badgeColumn("Status", 120, s -> UiUtil.statusText(s.getStatus().toString())),
                UiUtil.highlight(UiUtil.<IngredientStock>numberColumn("Expired (write off)", 130, s -> s.getExpiredQuantity().signum() == 0
                        ? "-" : Formatters.quantity(s.getExpiredQuantity(), s.getIngredient().getBaseUnit())),
                        value -> !"-".equals(value), "text-danger"),
                UiUtil.<IngredientStock>column("Nearest expiry", 120, s -> Formatters.date(s.getNearestExpiry())),
                UiUtil.<IngredientStock>numberColumn("Value", 110, s -> Formatters.money(s.getValue()))));
        UiUtil.placeholder(table, "No ingredients yet. Add flour, butter, sugar... or import the starter templates.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, stock) -> showLots(stock));
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && can(Permission.MANAGE_INGREDIENTS)) {
                onEdit();
            }
        });

        lotsTable.getColumns().setAll(Arrays.asList(
                UiUtil.<IngredientLot>column("Lot", 150, IngredientLot::getLotCode),
                UiUtil.<IngredientLot>column("Received", 140, l -> Formatters.dateTime(l.getReceivedAt(), zone())),
                UiUtil.<IngredientLot>column("Expiry", 110, l -> l.describeExpiry(zone())),
                UiUtil.<IngredientLot>numberColumn("Initial", 100, l -> Formatters.quantity(l.getInitialQuantity())),
                UiUtil.<IngredientLot>numberColumn("Remaining", 100, l -> Formatters.quantity(l.getRemainingQuantity())),
                UiUtil.<IngredientLot>numberColumn("Cost / unit", 100, l -> Formatters.unitCost(l.getUnitCost())),
                UiUtil.<IngredientLot>badgeColumn("State", 110, l -> l.isDepleted() ? "Used up"
                        : l.isExpiredAt(ctx.getClock().instant(), zone()) ? "Expired" : "Usable")));
        UiUtil.placeholder(lotsTable, "Select an ingredient to see its lots (FEFO: the earliest expiry is used first).");
        searchField.textProperty().addListener((obs, old, text) -> refresh());
        inactiveCheck.setOnAction(e -> refresh());

        boolean manage = can(Permission.MANAGE_INGREDIENTS);
        newButton.setDisable(!manage);
        editButton.setDisable(!manage);
        toggleButton.setDisable(!manage);
        receiveButton.setDisable(!can(Permission.RECEIVE_STOCK));
    }

    private ZoneId zone() {
        return ctx.settings().getSettings().zone();
    }

    @Override
    public void refresh() {
        List<IngredientStock> rows = ctx.ingredients().stockOverview(searchField.getText(), inactiveCheck.isSelected());
        UiUtil.setItems(table, rows);
        long attention = rows.stream().filter(IngredientStock::needsAttention).count();
        summaryLabel.setText(rows.size() + " ingredient(s)" + (attention > 0 ? "  ·  " + attention + " low or out of stock" : ""));
        showLots(table.getSelectionModel().getSelectedItem());
    }

    private void showLots(IngredientStock stock) {
        if (stock == null) {
            lotsTable.getItems().clear();
            lotsTitle.setText("Lots of the selected ingredient");
            return;
        }
        lotsTitle.setText("Lots of " + stock.getIngredient().getName() + " (" + stock.getIngredient().getBaseUnit() + ")");
        lotsTable.getItems().setAll(ctx.inventory().getIngredientLots(stock.getIngredient().getId(), true));
    }

    private Ingredient selected() {
        IngredientStock stock = table.getSelectionModel().getSelectedItem();
        if (stock == null) {
            Dialogs.info(window(), "Ingredients", "Select an ingredient first.");
            return null;
        }
        return stock.getIngredient();
    }

    @FXML
    private void onNew() {
        if (IngredientDialog.show(window(), ctx, null) != null) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        Ingredient ingredient = selected();
        if (ingredient != null && IngredientDialog.show(window(), ctx, ingredient) != null) {
            refresh();
        }
    }

    @FXML
    private void onToggle() {
        Ingredient ingredient = selected();
        if (ingredient != null) {
            attempt(() -> ctx.ingredients().setActive(ingredient.getId(), !ingredient.isActive()));
        }
    }

    @FXML
    private void onReceive() {
        if (ReceiveGoodsDialog.show(window(), ctx)) {
            refresh();
        }
    }
}
