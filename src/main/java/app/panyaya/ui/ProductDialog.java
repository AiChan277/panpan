package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.product.ProductType;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** Create / edit product form. */
final class ProductDialog {

    private ProductDialog() {
    }

    /**
     * @param existing product to edit, or null to create
     * @param fixedType  when not null the type is preset and locked (e.g. from the recipe editor)
     * @return the saved product, or null when cancelled
     */
    static Product show(Window owner, AppContext ctx, Product existing, ProductType fixedType) {
        boolean editing = existing != null;
        FormDialog form = new FormDialog(owner, editing ? "Edit product" : "New product", "Save product");

        TextField name = form.add("Product name *", UiUtil.field("e.g. Japanese Milk Bread", editing ? existing.getName() : ""));
        ComboBox<ProductCategory> category = new ComboBox<>();
        category.getItems().setAll(ctx.products().getCategories(false));
        category.setMaxWidth(Double.MAX_VALUE);
        form.add("Category *", category);

        TextField sku = UiUtil.field("2-20 letters, digits or '-'", editing ? existing.getSku() : "");
        Button suggest = new Button("Suggest");
        HBox skuRow = new HBox(6, sku, suggest);
        HBox.setHgrow(sku, Priority.ALWAYS);
        form.add("SKU *", skuRow);
        suggest.setOnAction(e -> sku.setText(ctx.products().suggestSku(name.getText(),
                category.getValue() == null ? null : category.getValue().getId())));

        ComboBox<ProductType> type = new ComboBox<>();
        type.getItems().setAll(ProductType.values());
        type.setValue(editing ? existing.getType() : (fixedType != null ? fixedType : ProductType.MANUFACTURED));
        type.setDisable(fixedType != null);
        type.setMaxWidth(Double.MAX_VALUE);
        form.add("Type *", type);

        TextField price = form.add("Selling price (Rp) *", UiUtil.field("e.g. 18000 or 18.000",
                editing ? Formatters.plainMoney(existing.getSellingPrice()) : ""));
        TextField shelfLife = form.add("Shelf life (hours) *", UiUtil.field("e.g. 48",
                editing ? String.valueOf(existing.getShelfLifeHours()) : "24"));
        TextArea description = form.add("Description", UiUtil.area("Optional", editing ? existing.getDescription() : "", 2));
        form.addHint("SKUs are unique (ignoring upper/lower case). Archived products stay in history and reports.");

        if (editing) {
            for (ProductCategory c : ctx.products().getCategories(true)) {
                if (c.getId() == existing.getCategoryId()) {
                    if (!category.getItems().contains(c)) {
                        category.getItems().add(c);
                    }
                    category.setValue(c);
                }
            }
        }

        final Product[] saved = new Product[1];
        boolean ok = form.show(() -> {
            if (category.getValue() == null) {
                throw new ValidationException("Please choose a category.");
            }
            if (sku.getText().isBlank()) {
                sku.setText(ctx.products().suggestSku(name.getText(), category.getValue().getId()));
            }
            int hours = NumberParser.parseWholeNumber(shelfLife.getText(), "Shelf life");
            if (editing) {
                saved[0] = ctx.products().updateProduct(existing.getId(), name.getText(), sku.getText(),
                        category.getValue().getId(), type.getValue(), NumberParser.parseMoney(price.getText(), "Price"),
                        hours, description.getText());
            } else {
                saved[0] = ctx.products().createProduct(name.getText(), sku.getText(), category.getValue().getId(),
                        type.getValue(), NumberParser.parseMoney(price.getText(), "Price"), hours, description.getText());
            }
        });
        return ok ? saved[0] : null;
    }
}
