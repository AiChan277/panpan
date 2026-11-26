package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.model.user.User;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.TaskRunner;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Sign-in screen. Password hashing is slow on purpose, so it runs in the background. */
public class LoginController {

    @FXML
    private Label bakeryLabel;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button signInButton;
    @FXML
    private HBox root;
    @FXML
    private VBox brandPanel;
    @FXML
    private Label versionLabel;

    private AppContext ctx;

    /** Presentation only: the brand panel gives way to the form on narrow windows. */
    @FXML
    private void initialize() {
        versionLabel.setText(AppContext.APP_NAME + " " + AppContext.APP_VERSION);
        root.widthProperty().addListener((obs, old, width) -> {
            boolean wide = width.doubleValue() >= 900;
            brandPanel.setVisible(wide);
            brandPanel.setManaged(wide);
        });
    }
    private TaskRunner tasks;
    private Runnable onSignedIn;

    public void init(AppContext context, TaskRunner taskRunner, Runnable signedInCallback) {
        this.ctx = context;
        this.tasks = taskRunner;
        this.onSignedIn = signedInCallback;
        bakeryLabel.setText(ctx.getStore().getData().getSettings().getBakeryName());
        usernameField.requestFocus();
    }

    @FXML
    private void onSignIn() {
        String username = usernameField.getText();
        char[] password = passwordField.getText().toCharArray();
        signInButton.setDisable(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        tasks.run(() -> ctx.auth().login(username, password), this::signedIn, error -> {
            signInButton.setDisable(false);
            passwordField.clear();
            errorLabel.setText(Dialogs.describe(error));
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            passwordField.requestFocus();
        });
    }

    private void signedIn(User user) {
        if (user.isMustChangePassword()) {
            boolean changed = PasswordDialogs.forceChange(usernameField.getScene().getWindow(), ctx);
            if (!changed) {
                ctx.auth().logout();
                signInButton.setDisable(false);
                passwordField.clear();
                return;
            }
        }
        onSignedIn.run();
    }
}
