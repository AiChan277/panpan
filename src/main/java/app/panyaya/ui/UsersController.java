package app.panyaya.ui;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.user.Role;
import app.panyaya.model.user.User;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.FormDialog;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.Arrays;

/** Owner-only user management. */
public class UsersController extends BaseController {

    @FXML
    private TableView<User> table;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<User>column("Username", 140, User::getUsername),
                UiUtil.<User>column("Name", 200, User::getDisplayName),
                UiUtil.<User>column("Role", 150, u -> u.getRole().toString()),
                UiUtil.<User>column("Status", 110, u -> u.isActive() ? "✓ Active" : "Deactivated"),
                UiUtil.<User>column("Password", 200, u -> u.isMustChangePassword() ? "must change at next sign-in" : "set by user"),
                UiUtil.<User>column("Created", 140, u -> Formatters.dateTime(u.getCreatedAt(), ctx.settings().getSettings().zone()))));
    }

    @Override
    public void refresh() {
        UiUtil.setItems(table, ctx.users().getUsers());
    }

    private User selected(String action) {
        User user = table.getSelectionModel().getSelectedItem();
        if (user == null) {
            Dialogs.info(window(), action, "Select a user first.");
        }
        return user;
    }

    @FXML
    private void onNew() {
        FormDialog form = new FormDialog(window(), "New user", "Create user");
        TextField username = form.add("Username *", UiUtil.field("e.g. sari", ""));
        TextField name = form.add("Display name *", UiUtil.field("e.g. Sari", ""));
        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setValue(Role.CASHIER);
        form.add("Role *", role);
        PasswordField password = form.add("Temporary password *", new PasswordField());
        form.addHint("Give the temporary password to the employee. They must choose their own password at the first sign-in.");
        if (form.show(() -> ctx.users().createUser(username.getText(), name.getText(), role.getValue(),
                password.getText().toCharArray()))) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        User user = selected("Edit user");
        if (user == null) {
            return;
        }
        FormDialog form = new FormDialog(window(), "Edit user", "Save");
        form.addHint("Username: " + user.getUsername());
        TextField name = form.add("Display name *", UiUtil.field("", user.getDisplayName()));
        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setValue(user.getRole());
        form.add("Role *", role);
        if (form.show(() -> ctx.users().updateUser(user.getId(), name.getText(), role.getValue()))) {
            refresh();
        }
    }

    @FXML
    private void onReset() {
        User user = selected("Reset password");
        if (user == null) {
            return;
        }
        FormDialog form = new FormDialog(window(), "Reset password of " + user.getUsername(), "Reset");
        PasswordField password = form.add("Temporary password *", new PasswordField());
        PasswordField repeat = form.add("Repeat", new PasswordField());
        if (form.show(() -> {
            if (!password.getText().equals(repeat.getText())) {
                throw new ValidationException("The two passwords are different.");
            }
            ctx.users().resetPassword(user.getId(), password.getText().toCharArray());
        })) {
            refresh();
        }
    }

    @FXML
    private void onToggle() {
        User user = selected("Activate / deactivate");
        if (user != null && Dialogs.confirm(window(), user.isActive() ? "Deactivate user" : "Activate user",
                (user.isActive() ? "Deactivate " : "Activate ") + user.getDisplayName() + "?"
                        + (user.isActive() ? " They can no longer sign in; their past work stays in history." : ""))) {
            attempt(() -> ctx.users().setActive(user.getId(), !user.isActive()));
        }
    }
}
