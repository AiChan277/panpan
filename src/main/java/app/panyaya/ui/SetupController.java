package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.TaskRunner;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** First-run wizard: bakery profile + Owner account (+ optional starter templates). */
public class SetupController {

    @FXML
    private TextField bakeryField;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField displayNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField repeatField;
    @FXML
    private CheckBox starterCheck;
    @FXML
    private Label errorLabel;
    @FXML
    private Button createButton;

    private AppContext ctx;
    private TaskRunner tasks;
    private Runnable onDone;

    public void init(AppContext context, TaskRunner taskRunner, Runnable doneCallback) {
        this.ctx = context;
        this.tasks = taskRunner;
        this.onDone = doneCallback;
        bakeryField.requestFocus();
    }

    @FXML
    private void onCreate() {
        if (!passwordField.getText().equals(repeatField.getText())) {
            showError("The two passwords are different.");
            return;
        }
        String bakery = bakeryField.getText();
        String username = usernameField.getText();
        String displayName = displayNameField.getText().isBlank() ? username : displayNameField.getText();
        char[] password = passwordField.getText().toCharArray();
        boolean starter = starterCheck.isSelected();
        createButton.setDisable(true);
        tasks.run(() -> {
            ctx.auth().setupNewBakery(bakery, username, displayName, password);
            if (starter) {
                ctx.seed().importStarterRecipes();
            }
            return Boolean.TRUE;
        }, ok -> onDone.run(), error -> {
            createButton.setDisable(false);
            showError(Dialogs.describe(error));
        });
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
