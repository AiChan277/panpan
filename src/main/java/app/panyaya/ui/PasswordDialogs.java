package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.exception.ValidationException;
import app.panyaya.ui.common.FormDialog;
import javafx.scene.control.PasswordField;
import javafx.stage.Window;

/** Change-password dialogs (voluntary and forced after a reset). */
final class PasswordDialogs {

    private PasswordDialogs() {
    }

    static boolean change(Window owner, AppContext ctx) {
        return show(owner, ctx, "Change password", null);
    }

    static boolean forceChange(Window owner, AppContext ctx) {
        return show(owner, ctx, "Choose a new password",
                "Your password was set by the owner. Please choose your own password now.");
    }

    private static boolean show(Window owner, AppContext ctx, String title, String hint) {
        FormDialog form = new FormDialog(owner, title, "Change password");
        if (hint != null) {
            form.addHint(hint);
        }
        PasswordField current = form.add("Current password", new PasswordField());
        PasswordField next = form.add("New password", new PasswordField());
        PasswordField repeat = form.add("Repeat new password", new PasswordField());
        form.addHint("At least 8 characters, different from your username.");
        return form.show(() -> {
            if (!next.getText().equals(repeat.getText())) {
                throw new ValidationException("The two new passwords are different.");
            }
            ctx.auth().changeOwnPassword(current.getText().toCharArray(), next.getText().toCharArray());
        });
    }
}
