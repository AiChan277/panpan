package app.panyaya.ui;

import app.panyaya.model.product.PriceChange;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.user.Permission;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.Arrays;
import java.util.List;

/** Products screen: list, search, filters, create/edit/archive and categories. */
public class ProductsController extends BaseController {

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<ProductCategory> categoryFilter;
    @FXML
    private ComboBox<ProductType> typeFilter;
    @FXML
    private CheckBox archivedCheck;
    @FXML
    private TableView<Product> table;
    @FXML
    private ListView<ProductCategory> categoryList;
    @FXML
    private Button newButton;
    @FXML
    private Button editButton;
    @FXML
    private Button archiveButton;
    @FXML
    private Button addCategoryButton;
    @FXML
    private Button renameCategoryButton;
    @FXML
    private Button toggleCategoryButton;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<Product>column("Name", 240, Product::getName),
                UiUtil.<Product>column("SKU", 120, Product::getSku),
                UiUtil.<Product>column("Category", 120, p -> ctx.products().categoryName(p.getCategoryId())),
                UiUtil.<Product>column("Type", 140, p -> p.getType().toString()),
                UiUtil.<Product>numberColumn("Price", 110, p -> Formatters.money(p.getSellingPrice())),
                UiUtil.<Product>numberColumn("Sellable", 80,
                        p -> Formatters.quantity(ctx.inventory().sellableQuantity(p.getId()))),
                UiUtil.<Product>numberColumn("Shelf life", 80, p -> p.getShelfLifeHours() + " h"),
                UiUtil.<Product>badgeColumn("Status", 100, p -> p.isActive() ? "Active" : "Archived")));
        UiUtil.placeholder(table, "No products yet - click \"New product\" to add one, e.g. Japanese Milk Bread.");
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && can(Permission.MANAGE_PRODUCTS)) {
                onEdit();
            }
        });
        UiUtil.allOption(typeFilter, "All types", Arrays.asList(ProductType.values()));
        searchField.textProperty().addListener((obs, old, text) -> refreshTable());
        categoryFilter.setOnAction(e -> refreshTable());
        typeFilter.setOnAction(e -> refreshTable());
        archivedCheck.setOnAction(e -> refreshTable());
        categoryList.setCellFactory(list -> new ListCell<ProductCategory>() {
            @Override
            protected void updateItem(ProductCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + (item.isActive() ? "" : "  (inactive)"));
            }
        });

        boolean manage = can(Permission.MANAGE_PRODUCTS);
        for (Button button : Arrays.asList(newButton, editButton, archiveButton, addCategoryButton,
                renameCategoryButton, toggleCategoryButton)) {
            button.setDisable(!manage);
        }
    }

    @Override
    public void refresh() {
        List<ProductCategory> all = ctx.products().getCategories(true);
        categoryList.setItems(FXCollections.observableArrayList(all));
        UiUtil.allOption(categoryFilter, "All categories", all);
        refreshTable();
    }

    private void refreshTable() {
        ProductCategory category = categoryFilter.getValue();
        UiUtil.setItems(table, ctx.products().search(searchField.getText(),
                category == null ? null : category.getId(), typeFilter.getValue(), archivedCheck.isSelected()));
    }

    @FXML
    private void onNew() {
        if (ProductDialog.show(window(), ctx, null, null) != null) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info(window(), "Edit product", "Select a product first.");
            return;
        }
        if (ProductDialog.show(window(), ctx, selected, null) != null) {
            refresh();
        }
    }

    @FXML
    private void onArchive() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info(window(), "Archive", "Select a product first.");
            return;
        }
        boolean archive = selected.isActive();
        String question = archive
                ? "Archive \"" + selected.getName() + "\"? It disappears from the POS and production planning but stays in history."
                : "Restore \"" + selected.getName() + "\" so it can be sold and produced again?";
        if (archive ? Dialogs.confirmDanger(window(), "Archive product", question, "Archive")
                : Dialogs.confirm(window(), "Restore product", question)) {
            attempt(() -> ctx.products().setArchived(selected.getId(), archive));
        }
    }

    @FXML
    private void onPriceHistory() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info(window(), "Price history", "Select a product first.");
            return;
        }
        StringBuilder text = new StringBuilder("Price history of " + selected.getName() + "\n\n");
        if (selected.getPriceHistory().isEmpty()) {
            text.append("The price has never changed (").append(Formatters.money(selected.getSellingPrice())).append(").");
        }
        for (PriceChange change : selected.getPriceHistory()) {
            text.append(Formatters.dateTime(change.getChangedAt(), ctx.settings().getSettings().zone())).append("  ")
                    .append(Formatters.money(change.getOldPrice())).append(" -> ")
                    .append(Formatters.money(change.getNewPrice())).append('\n');
        }
        Dialogs.showText(window(), "Price history", text.toString(), null, null);
    }

    @FXML
    private void onAddCategory() {
        String name = Dialogs.ask(window(), "New category", "Category name (e.g. Bread, Pastry, Cake):", "");
        if (name != null) {
            attempt(() -> ctx.products().createCategory(name));
        }
    }

    @FXML
    private void onRenameCategory() {
        ProductCategory selected = categoryList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String name = Dialogs.ask(window(), "Rename category", "New name:", selected.getName());
        if (name != null) {
            attempt(() -> ctx.products().renameCategory(selected.getId(), name));
        }
    }

    @FXML
    private void onToggleCategory() {
        ProductCategory selected = categoryList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            attempt(() -> ctx.products().setCategoryActive(selected.getId(), !selected.isActive()));
        }
    }
}
