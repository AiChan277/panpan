package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.sales.Cart;
import app.panyaya.model.sales.PaymentInfo;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

import java.math.BigDecimal;

/** Payment: method, cash received with quick buttons, and the change shown in large type. */
final class PaymentDialog {

    private PaymentDialog() {
    }

    static Sale show(Window owner, AppContext ctx, Cart cart) {
        BigDecimal total = cart.getTotal();
        FormDialog form = new FormDialog(owner, "Payment", "Complete sale");
        Label totalLabel = new Label(Formatters.money(total));
        totalLabel.getStyleClass().add("cart-total");
        form.add("Total", totalLabel);

        ComboBox<PaymentMethod> method = new ComboBox<>();
        method.getItems().setAll(PaymentMethod.values());
        method.setValue(PaymentMethod.CASH);
        form.add("Method", method);
        TextField tendered = form.add("Cash received (Rp)", UiUtil.field("e.g. 50.000", ""));
        Button exact = new Button("Exact");
        Button fifty = new Button("50.000");
        Button hundred = new Button("100.000");
        HBox quick = form.add("", new HBox(6, exact, fifty, hundred));
        Label change = new Label("Change: -");
        change.getStyleClass().add("change-amount");
        form.add("", change);
        TextField reference = form.add("Reference", UiUtil.field("for \"Other\": e.g. voucher no.", ""));

        exact.setOnAction(e -> tendered.setText(Formatters.plainMoney(total)));
        fifty.setOnAction(e -> tendered.setText("50000"));
        hundred.setOnAction(e -> tendered.setText("100000"));
        Runnable update = () -> {
            boolean cash = method.getValue() == PaymentMethod.CASH;
            tendered.setDisable(!cash);
            quick.setDisable(!cash);
            if (!cash) {
                change.setText("Change: -");
                return;
            }
            try {
                BigDecimal received = NumberParser.parseMoney(tendered.getText(), "Cash received");
                BigDecimal difference = received.subtract(total);
                change.setText(difference.signum() < 0 ? "Short: " + Formatters.money(difference.negate())
                        : "Change: " + Formatters.money(difference));
            } catch (ValidationException ex) {
                change.setText("Change: -");
            }
        };
        tendered.textProperty().addListener((obs, old, text) -> update.run());
        method.setOnAction(e -> update.run());
        Platform.runLater(tendered::requestFocus);

        final Sale[] sale = new Sale[1];
        boolean ok = form.show(() -> {
            BigDecimal received = null;
            if (method.getValue() == PaymentMethod.CASH) {
                received = NumberParser.parseMoney(tendered.getText(), "Cash received");
            }
            // checkout re-checks stock and saves everything at once; failure keeps the cart
            sale[0] = ctx.sales().checkout(cart, new PaymentInfo(method.getValue(), received, reference.getText()));
        });
        return ok ? sale[0] : null;
    }
}
