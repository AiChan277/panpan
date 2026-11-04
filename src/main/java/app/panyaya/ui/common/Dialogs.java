package app.panyaya.ui.common;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.DataCorruptedException;
import app.panyaya.exception.InsufficientStockException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.util.AppLog;
import app.panyaya.util.TextUtil;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Standard dialogs. Errors are explained in plain language; stack traces only go to the log file. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void info(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        prepare(alert, owner, title);
        alert.showAndWait();
    }

    /** Shows an exception: business rules as warnings, save failures as errors, bugs with a reference. */
    public static void error(Window owner, Throwable error) {
        Throwable cause = unwrap(error);
        Alert alert;
        if (cause instanceof InsufficientStockException) {
            alert = new Alert(Alert.AlertType.WARNING, cause.getMessage(), ButtonType.OK);
            prepare(alert, owner, "Not enough stock");
            alert.setHeaderText("Nothing was saved. Receive goods or adjust the plan, then try again.");
        } else if (cause instanceof BusinessException) {
            alert = new Alert(Alert.AlertType.WARNING, cause.getMessage(), ButtonType.OK);
            prepare(alert, owner, "Cannot do that");
        } else if (cause instanceof DataCorruptedException) {
            alert = new Alert(Alert.AlertType.ERROR, cause.getMessage(), ButtonType.OK);
            prepare(alert, owner, "Invalid data");
        } else if (cause instanceof PersistenceException) {
            AppLog.error("Save failed", cause);
            alert = new Alert(Alert.AlertType.ERROR, cause.getMessage()
                    + "\n\nYour input was kept - you can try again.", ButtonType.OK);
            prepare(alert, owner, "Saving failed");
            alert.setHeaderText("Nothing was saved.");
        } else {
            String reference = UUID.randomUUID().toString().substring(0, 8);
            AppLog.error("Unexpected error [" + reference + "]", cause);
            alert = new Alert(Alert.AlertType.ERROR, "Something unexpected went wrong. Nothing was saved.\n"
                    + "Reference for support: " + reference + " (see the log folder).", ButtonType.OK);
            prepare(alert, owner, "Unexpected error");
        }
        alert.showAndWait();
    }

    /** Text of an exception for inline labels (forms). */
    public static String describe(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BusinessException || cause instanceof PersistenceException) {
            return cause.getMessage();
        }
        AppLog.error("Unexpected error in form", cause);
        return "Unexpected error: " + cause.getClass().getSimpleName() + ". Nothing was saved.";
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && !(current instanceof BusinessException)
                && !(current instanceof PersistenceException)) {
            current = current.getCause();
        }
        return current;
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        prepare(alert, owner, title);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    /** For dangerous actions (restore, start over): the user must type a word. */
    public static boolean confirmTyped(Window owner, String title, String message, String requiredWord) {
        Dialog<ButtonType> dialog = new Dialog<>();
        prepare(dialog, owner, title);
        TextField field = new TextField();
        field.setPromptText(requiredWord);
        VBox box = new VBox(10, new Label(message), new Label("Type " + requiredWord + " to continue:"), field);
        box.setPrefWidth(460);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(field.textProperty().isNotEqualTo(requiredWord));
        Optional<ButtonType> answer = dialog.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    /** Asks for a short text (e.g. a reason). Returns null when cancelled. */
    public static String ask(Window owner, String title, String prompt, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial == null ? "" : initial);
        prepare(dialog, owner, title);
        dialog.setHeaderText(prompt);
        Optional<String> answer = dialog.showAndWait();
        return answer.isPresent() ? answer.get() : null;
    }

    /** Shows a monospaced text, e.g. a receipt or an integrity report, with an optional extra button. */
    public static void showText(Window owner, String title, String text, String extraButton, UiAction extraAction) {
        Dialog<ButtonType> dialog = new Dialog<>();
        prepare(dialog, owner, title);
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.getStyleClass().add("mono");
        area.setPrefSize(440, 460);
        dialog.getDialogPane().setContent(area);
        ButtonType extra = null;
        if (extraButton != null) {
            extra = new ButtonType(extraButton, ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().add(extra);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (extra != null) {
            Button extraNode = (Button) dialog.getDialogPane().lookupButton(extra);
            extraNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                event.consume(); // keep the dialog open
                try {
                    extraAction.run();
                } catch (Exception e) {
                    error(owner, e);
                }
            });
        }
        dialog.showAndWait();
    }

    public static Path chooseSaveFile(Window owner, String title, String suggestedName, String description,
                                      String extension, Path initialDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, "*" + extension));
        if (initialDir != null && initialDir.toFile().isDirectory()) {
            chooser.setInitialDirectory(initialDir.toFile());
        }
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return null;
        }
        String name = file.getName();
        return name.toLowerCase().endsWith(extension) ? file.toPath() : file.toPath().resolveSibling(name + extension);
    }

    public static Path chooseOpenFile(Window owner, String title, String description, String extension, Path initialDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, "*" + extension));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        if (initialDir != null && initialDir.toFile().isDirectory()) {
            chooser.setInitialDirectory(initialDir.toFile());
        }
        File file = chooser.showOpenDialog(owner);
        return file == null ? null : file.toPath();
    }

    static void prepare(Dialog<?> dialog, Window owner, String title) {
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(TextUtil.isBlank(title) ? "Pan-ya Ya" : title);
        dialog.setResizable(true);
        dialog.getDialogPane().getStylesheets().add(Dialogs.class.getResource("/app/panyaya/ui/css/panyaya.css")
                .toExternalForm());
        if (dialog instanceof Alert) {
            ((Alert) dialog).setHeaderText(null);
        }
    }
}
