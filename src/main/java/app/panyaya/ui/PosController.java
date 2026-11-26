package app.panyaya.ui;

import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import app.panyaya.ui.common.Badge;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.product.Product;
import app.panyaya.model.sales.Cart;
import app.panyaya.model.sales.CartLine;
import app.panyaya.model.sales.Sale;
import app.panyaya.service.ProductAvailability;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Point of sale. The cart lives in memory; nothing is saved until "Pay" succeeds. If the checkout
 * fails (e.g. the last bread was just written off), the cart stays exactly as it was.
 */
public class PosController extends BaseController {

    @FXML
    private TextField searchField;
    @FXML
    private Label tileInfo;
    @FXML
    private FlowPane tilePane;
    @FXML
    private TableView<CartLine> cartTable;
    @FXML
    private Label itemCountLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Button payButton;
    @FXML
    private Label lastSaleLabel;

    /** Product tiles: 176 x 116 px minus padding (see .pos-tile in the stylesheet). */
    private static final double TILE_CONTENT_WIDTH = 148;
    private static final double TILE_CONTENT_HEIGHT = 92;

    private final Cart cart = new Cart();
    private List<ProductAvailability> available;

    @Override
    protected void setUp() {
        cartTable.getColumns().setAll(Arrays.asList(
                UiUtil.<CartLine>column("Product", 160, CartLine::getName),
                UiUtil.<CartLine>numberColumn("Qty", 50, l -> String.valueOf(l.getQuantity())),
                UiUtil.<CartLine>numberColumn("Price", 80, l -> Formatters.money(l.getUnitPrice())),
                UiUtil.<CartLine>numberColumn("Total", 90, l -> Formatters.money(l.getLineTotal()))));
        UiUtil.placeholder(cartTable, "Click a product or type its name / SKU.");
        searchField.textProperty().addListener((obs, old, text) -> showTiles());
        searchField.setOnAction(e -> addFirstMatch());

        // keyboard map: F2 search, F12 pay, Delete remove, +/- quantity
        tilePane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
            }
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
            }
        });
    }

    private void onKey(KeyEvent event) {
        Scene scene = tilePane.getScene();
        if (scene == null || !tilePane.isVisible() || tilePane.getScene().getWindow() == null) {
            return;
        }
        if (event.getCode() == KeyCode.F2) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
        } else if (event.getCode() == KeyCode.F12) {
            onPay();
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE && !searchField.isFocused()) {
            onRemove();
            event.consume();
        } else if ((event.getCode() == KeyCode.ADD || event.getCode() == KeyCode.PLUS) && !searchField.isFocused()) {
            onPlus();
            event.consume();
        } else if ((event.getCode() == KeyCode.SUBTRACT || event.getCode() == KeyCode.MINUS) && !searchField.isFocused()) {
            onMinus();
            event.consume();
        }
    }

    @Override
    public void refresh() {
        available = ctx.sales().getSellableProducts("");
        showTiles();
        showCart();
        Platform.runLater(() -> searchField.requestFocus());
    }

    private void showTiles() {
        tilePane.getChildren().clear();
        String query = searchField.getText();
        int shown = 0;
        for (ProductAvailability item : available) {
            Product product = item.getProduct();
            if (!app.panyaya.util.TextUtil.containsIgnoreCase(product.getName(), query)
                    && !app.panyaya.util.TextUtil.containsIgnoreCase(product.getSku(), query)) {
                continue;
            }
            int left = item.getSellablePieces() - cart.quantityOf(product.getId());
            Label name = new Label(product.getName());
            name.getStyleClass().add("tile-name");
            name.setWrapText(true);
            name.setMaxWidth(TILE_CONTENT_WIDTH);
            name.setMaxHeight(44); // two lines, then "..."
            Label price = new Label(Formatters.money(product.getSellingPrice()));
            price.getStyleClass().add("tile-price");
            Label stock = left > 0 ? new Label(left + " left") : new Badge("Sold out", Badge.Tone.NEUTRAL);
            if (left > 0) {
                stock.getStyleClass().add("tile-stock");
            }
            Region push = new Region();
            VBox.setVgrow(push, Priority.ALWAYS);
            VBox content = new VBox(4, name, push, price, stock);
            content.setPrefSize(TILE_CONTENT_WIDTH, TILE_CONTENT_HEIGHT);
            content.setMinSize(TILE_CONTENT_WIDTH, TILE_CONTENT_HEIGHT);
            Button tile = new Button();
            tile.setGraphic(content);
            tile.getStyleClass().add("pos-tile");
            tile.setDisable(left <= 0);
            tile.setOnAction(e -> addToCart(product));
            tilePane.getChildren().add(tile);
            shown++;
        }
        boolean nothingInStock = true;
        for (ProductAvailability item : available) {
            if (item.getSellablePieces() > 0) {
                nothingInStock = false;
            }
        }
        if (shown == 0) {
            tileInfo.setText(available.isEmpty()
                    ? "No active products. Add products and produce or receive stock first." : "No product matches.");
        } else if (nothingInStock) {
            // products exist but none can be sold: say where sellable stock comes from
            tileInfo.setText(shown + " product(s), all sold out. Sellable stock is created by completing a production "
                    + "order (Production: Plan > Start > Complete) for baked products, or by Inventory & Receiving > "
                    + "Receive goods for resale products.");
        } else {
            tileInfo.setText(shown + " product(s). Only stock that is not expired can be sold.");
        }
    }

    private void addFirstMatch() {
        String query = searchField.getText();
        Product exact = ctx.sales().findBySku(query); // barcode scanners type the SKU + Enter
        if (exact != null) {
            addToCart(exact);
            searchField.clear();
            return;
        }
        for (ProductAvailability item : available) {
            Product product = item.getProduct();
            if ((app.panyaya.util.TextUtil.containsIgnoreCase(product.getName(), query)
                    || app.panyaya.util.TextUtil.containsIgnoreCase(product.getSku(), query))
                    && item.getSellablePieces() > cart.quantityOf(product.getId())) {
                addToCart(product);
                searchField.clear();
                return;
            }
        }
    }

    private void addToCart(Product product) {
        try {
            int sellable = 0;
            for (ProductAvailability item : available) {
                if (item.getProduct().getId() == product.getId()) {
                    sellable = item.getSellablePieces();
                }
            }
            if (cart.quantityOf(product.getId()) + 1 > sellable) {
                throw new ValidationException("Only " + sellable + " x " + product.getName() + " can be sold right now.");
            }
            cart.add(product, 1);
            showCart();
            showTiles();
            selectLine(product.getId());
        } catch (ValidationException e) {
            Dialogs.error(window(), e);
        }
    }

    private void selectLine(long productId) {
        for (CartLine line : cartTable.getItems()) {
            if (line.getProductId() == productId) {
                cartTable.getSelectionModel().select(line);
            }
        }
    }

    private void showCart() {
        // CartLine objects are reused when the quantity changes; setItems also forces a redraw
        UiUtil.setItems(cartTable, cart.getLines());
        totalLabel.setText(Formatters.money(cart.getTotal()));
        itemCountLabel.setText(cart.getItemCount() + (cart.getItemCount() == 1 ? " item" : " items"));
        payButton.setDisable(cart.isEmpty());
    }

    private CartLine selectedLine() {
        CartLine line = cartTable.getSelectionModel().getSelectedItem();
        if (line == null && !cartTable.getItems().isEmpty()) {
            line = cartTable.getItems().get(cartTable.getItems().size() - 1);
        }
        return line;
    }

    @FXML
    private void onPlus() {
        CartLine line = selectedLine();
        if (line != null) {
            addToCart(ctx.products().getProduct(line.getProductId()));
        }
    }

    @FXML
    private void onMinus() {
        CartLine line = selectedLine();
        if (line == null) {
            return;
        }
        try {
            cart.setQuantity(line.getProductId(), line.getQuantity() - 1);
            showCart();
            showTiles();
            selectLine(line.getProductId());
        } catch (ValidationException e) {
            Dialogs.error(window(), e);
        }
    }

    @FXML
    private void onRemove() {
        CartLine line = cartTable.getSelectionModel().getSelectedItem();
        if (line != null) {
            cart.remove(line.getProductId());
            showCart();
            showTiles();
        }
    }

    @FXML
    private void onClear() {
        if (!cart.isEmpty() && Dialogs.confirmDanger(window(), "Clear cart", "Remove all items from the cart?", "Clear cart")) {
            cart.clear();
            showCart();
            showTiles();
        }
    }

    @FXML
    private void onPay() {
        if (cart.isEmpty()) {
            return;
        }
        payButton.setDisable(true); // prevents a double checkout
        Sale sale = PaymentDialog.show(window(), ctx, cart);
        if (sale == null) {
            payButton.setDisable(false);
            refresh(); // stock may have changed meanwhile; the cart is kept
            return;
        }
        cart.clear();
        lastSaleLabel.setText("Last sale " + sale.getSaleNo() + ": " + Formatters.money(sale.getTotal())
                + (sale.getChangeGiven().signum() > 0 ? ", change " + Formatters.money(sale.getChangeGiven()) : ""));
        showReceipt(sale);
        refresh();
    }

    private void showReceipt(Sale sale) {
        String text = ctx.sales().receiptText(sale);
        Dialogs.showText(window(), "Receipt " + sale.getSaleNo(), text, "Save as text file...", () -> {
            Path file = Dialogs.chooseSaveFile(window(), "Save receipt", sale.getSaleNo() + ".txt", "Text file", ".txt",
                    ctx.getPaths().getExportDir());
            if (file != null) {
                ctx.sales().saveReceipt(sale, file);
                shell.setStatus("Receipt saved to " + file);
            }
        });
    }
}
