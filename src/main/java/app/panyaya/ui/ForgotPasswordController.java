package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.Icon;
import app.panyaya.ui.common.TaskRunner;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Controller untuk layar "Lupa Kata Sandi".
 *
 * <p>Alur dua langkah:
 * <ol>
 *   <li>User memasukkan username → aplikasi menampilkan pertanyaan keamanan →
 *       user menjawab → jika benar, lanjut ke langkah 2.</li>
 *   <li>User memasukkan kata sandi baru dua kali → disimpan.</li>
 * </ol>
 *
 * <p>Hashing berjalan di background thread (lewat {@link TaskRunner}) agar UI tidak freeze.
 */
public class ForgotPasswordController {

    // ── FXML injections ──────────────────────────────────────────────────────

    @FXML private HBox      root;
    @FXML private VBox      brandPanel;
    @FXML private Label     versionLabel;
    @FXML private Label     bakeryLabel;

    // Langkah 1 – verifikasi
    @FXML private VBox      stepVerifyPane;
    @FXML private TextField usernameField;
    @FXML private VBox      securityQuestionPane;
    @FXML private Label     questionLabel;
    @FXML private TextField answerField;
    @FXML private Button    lookupButton;
    @FXML private Button    verifyButton;

    // Langkah 2 – reset
    @FXML private VBox          stepResetPane;
    @FXML private Label         resetSubtitle;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button        resetButton;

    // Error / info label bersama
    @FXML private Label errorLabel;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Username yang sudah lolos verifikasi pertanyaan; non-null mulai Langkah 2. */
    private String verifiedUsername;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private AppContext ctx;
    private TaskRunner tasks;
    private Runnable   onBack;

    // ── Init (dipanggil oleh PanyaApp) ────────────────────────────────────────

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

    public void init(AppContext context, TaskRunner taskRunner, Runnable backCallback) {
        this.ctx    = context;
        this.tasks  = taskRunner;
        this.onBack = backCallback;
        bakeryLabel.setText(ctx.getStore().getData().getSettings().getBakeryName());
        usernameField.requestFocus();
    }

    // ── Handlers – Langkah 1 ─────────────────────────────────────────────────

    /**
     * Dipanggil oleh tombol "Lanjutkan" atau Enter di field username.
     * Mencari pertanyaan keamanan milik username tersebut.
     */
    @FXML
    private void onLookup() {
        clearError();
        String username = usernameField.getText().strip();
        if (username.isEmpty()) {
            showError("Masukkan username Anda terlebih dahulu.");
            return;
        }

        String question = ctx.users().getSecurityQuestion(username);
        if (question == null || question.isBlank()) {
            showError("Username tidak ditemukan, akun tidak aktif, atau belum memiliki " +
                      "pertanyaan keamanan. Hubungi Owner untuk mereset kata sandi Anda.");
            return;
        }

        // Tampilkan pertanyaan, sembunyikan tombol Lanjutkan
        questionLabel.setText("Pertanyaan: " + question);
        securityQuestionPane.setManaged(true);
        securityQuestionPane.setVisible(true);
        lookupButton.setManaged(false);
        lookupButton.setVisible(false);
        usernameField.setDisable(true);
        answerField.requestFocus();
    }

    /**
     * Dipanggil oleh tombol "Verifikasi" atau Enter di field jawaban.
     * Memverifikasi jawaban ke AuthService sebelum lanjut ke Langkah 2.
     */
    @FXML
    private void onVerify() {
        clearError();
        String username = usernameField.getText().strip();
        String answer = answerField.getText().strip();
        if (answer.isEmpty()) {
            showError("Masukkan jawaban pertanyaan keamanan.");
            answerField.requestFocus();
            return;
        }
        verifyButton.setDisable(true);
        try {
            ctx.auth().verifySecurityAnswer(username, answer);
        } catch (Exception ex) {
            verifyButton.setDisable(false);
            answerField.clear();
            answerField.requestFocus();
            showError(Dialogs.describe(ex));
            return;
        }

        this.verifiedUsername = username;
        stepVerifyPane.setManaged(false);
        stepVerifyPane.setVisible(false);
        resetSubtitle.setText("Akun: " + verifiedUsername);
        stepResetPane.setManaged(true);
        stepResetPane.setVisible(true);
        verifyButton.setDisable(false);
        newPasswordField.requestFocus();
    }

    // ── Handlers – Langkah 2 ─────────────────────────────────────────────────

    /**
     * Dipanggil oleh tombol "Simpan kata sandi baru".
     * Verifikasi jawaban DAN hash kata sandi baru terjadi di background thread.
     */
    @FXML
    private void onReset() {
        clearError();
        String newPwd  = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (newPwd.isEmpty()) {
            showError("Masukkan kata sandi baru.");
            newPasswordField.requestFocus();
            return;
        }
        if (!newPwd.equals(confirm)) {
            showError("Kata sandi baru dan konfirmasi tidak cocok.");
            confirmPasswordField.clear();
            confirmPasswordField.requestFocus();
            return;
        }

        String username = verifiedUsername;
        String answer   = answerField.getText().strip();
        char[] password = newPwd.toCharArray();

        resetButton.setDisable(true);

        // Hashing di background (Argon2/BCrypt bisa > 500 ms)
        tasks.run(
                () -> {
                    ctx.auth().resetPasswordWithSecurityAnswer(username, answer, password);
                    return null;
                },
                result -> onResetSuccess(),
                error  -> {
                    resetButton.setDisable(false);
                    newPasswordField.clear();
                    confirmPasswordField.clear();
                    showError(Dialogs.describe(error));
                }
        );
    }

    private void onResetSuccess() {
        newPasswordField.clear();
        confirmPasswordField.clear();
        showSuccess("Kata sandi berhasil diubah! Kembali ke halaman masuk dalam 2 detik...");
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> onBack.run());
        pause.play();
    }

    // ── Navigasi ─────────────────────────────────────────────────────────────

    @FXML
    private void onBack() {
        onBack.run();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String message) {
        errorLabel.getStyleClass().remove("form-success");
        if (!errorLabel.getStyleClass().contains("form-error")) {
            errorLabel.getStyleClass().add("form-error");
        }
        errorLabel.setGraphic(new Icon("x-circle", 16));
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void showSuccess(String message) {
        errorLabel.getStyleClass().remove("form-error");
        if (!errorLabel.getStyleClass().contains("form-success")) {
            errorLabel.getStyleClass().add("form-success");
        }
        errorLabel.setGraphic(new Icon("check-circle", 16));
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setGraphic(null);
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
}
