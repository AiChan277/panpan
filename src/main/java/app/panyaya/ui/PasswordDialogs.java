package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.user.User;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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

    static boolean securityQA(Window owner, AppContext ctx) {
        User current = ctx.getSession().getUser();
        String existingQuestion = current != null ? current.getSecurityQuestion() : "";
        FormDialog form = new FormDialog(owner, "Security question & answer", "Save");
        form.addHint("Used to reset your password if you forget it at sign-in.");
        TextField question = form.add("Security question *",
                UiUtil.field("e.g. Nama hewan peliharaan pertama?", existingQuestion));
        TextField answer = form.add("Security answer *",
                UiUtil.field("Jawaban (case-insensitive)", ""));
        return form.show(() -> {
            String q = question.getText().strip();
            String a = answer.getText().strip();
            if (q.isEmpty() || a.isEmpty()) {
                throw new ValidationException("Pertanyaan dan jawaban keamanan wajib diisi.");
            }
            ctx.users().setSecurityQA(q, a);
        });
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
