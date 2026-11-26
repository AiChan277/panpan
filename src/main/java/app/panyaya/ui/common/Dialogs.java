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
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Standard dialogs. Errors are explained in plain language; stack traces only go to the log file.
 *
 * <p>Every alert shows a toned icon and a heading, so the kind of message is clear at a glance:
 * information, success, warning (a business rule said no), error, or a destructive confirmation.</p>
 */
public final class Dialogs {

    private static final String CSS = "/app/panyaya/ui/css/panyaya.css";

    private Dialogs() {
    }

    public static void info(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        prepare(alert, owner, title);
        decorate(alert, title, Badge.Tone.INFO, "info");
        alert.showAndWait();
    }

    /** Confirms that something was done (saved, created, restored...). */
    public static void success(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        prepare(alert, owner, title);
        decorate(alert, title, Badge.Tone.SUCCESS, "check-circle");
        alert.showAndWait();
    }

    /** Shows an exception: business rules as warnings, save failures as errors, bugs with a reference. */
    public static void error(Window owner, Throwable error) {
        Throwable cause = unwrap(error);
        Alert alert;
        String heading;
        Badge.Tone tone;
        if (cause instanceof InsufficientStockException) {
            alert = new Alert(Alert.AlertType.WARNING, cause.getMessage(), ButtonType.OK);
            heading = "Not enough stock";
            tone = Badge.Tone.WARNING;
            prepare(alert, owner, heading);
            alert.setHeaderText("Nothing was saved. Receive goods or adjust the plan, then try again.");
        } else if (cause instanceof BusinessException) {
            alert = new Alert(Alert.AlertType.WARNING, cause.getMessage(), ButtonType.OK);
            heading = "Cannot do that";
            tone = Badge.Tone.WARNING;
            prepare(alert, owner, heading);
        } else if (cause instanceof DataCorruptedException) {
            alert = new Alert(Alert.AlertType.ERROR, cause.getMessage(), ButtonType.OK);
            heading = "Invalid data";
            tone = Badge.Tone.DANGER;
            prepare(alert, owner, heading);
        } else if (cause instanceof PersistenceException) {
            AppLog.error("Save failed", cause);
            alert = new Alert(Alert.AlertType.ERROR, cause.getMessage()
                    + "\n\nYour input was kept - you can try again.", ButtonType.OK);
            heading = "Saving failed";
            tone = Badge.Tone.DANGER;
            prepare(alert, owner, heading);
            alert.setHeaderText("Nothing was saved.");
        } else {
            String reference = UUID.randomUUID().toString().substring(0, 8);
            AppLog.error("Unexpected error [" + reference + "]", cause);
            alert = new Alert(Alert.AlertType.ERROR, "Something unexpected went wrong. Nothing was saved.\n"
                    + "Reference for support: " + reference + " (see the log folder).", ButtonType.OK);
            heading = "Unexpected error";
            tone = Badge.Tone.DANGER;
            prepare(alert, owner, heading);
        }
        decorate(alert, heading, tone, tone == Badge.Tone.WARNING ? "alert" : "x-circle");
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
        decorate(alert, title, Badge.Tone.INFO, "info");
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    /**
     * Confirmation for actions that remove or write off something. The confirming button names the
     * action and is drawn in the danger colour; Cancel stays the default, so Enter does not destroy.
     */
    public static boolean confirmDanger(Window owner, String title, String message, String actionLabel) {
        ButtonType action = new ButtonType(actionLabel, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, action, ButtonType.CANCEL);
        prepare(alert, owner, title);
        decorate(alert, title, Badge.Tone.DANGER, "alert");
        Button actionButton = (Button) alert.getDialogPane().lookupButton(action);
        actionButton.setDefaultButton(false);
        actionButton.getStyleClass().add("danger-solid");
        Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancel.setDefaultButton(true);
        cancel.getStyleClass().add("plain");
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == action;
    }

    /** For dangerous actions (restore, start over): the user must type a word. */
    public static boolean confirmTyped(Window owner, String title, String message, String requiredWord) {
        Dialog<ButtonType> dialog = new Dialog<>();
        prepare(dialog, owner, title);
        TextField field = new TextField();
        field.setPromptText(requiredWord);
        Label prompt = new Label("Type " + requiredWord + " to continue:");
        prompt.getStyleClass().add("form-label");
        VBox body = textBlock(title, null, message);
        body.getChildren().addAll(prompt, field);
        HBox box = new HBox(14, toneIcon(Badge.Tone.DANGER, "alert"), body);
        box.setPrefWidth(500);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(field.textProperty().isNotEqualTo(requiredWord));
        ok.setDefaultButton(false);
        ok.getStyleClass().add("danger-solid");
        Optional<ButtonType> answer = dialog.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    /** Asks for a short text (e.g. a reason). Returns null when cancelled. */
    public static String ask(Window owner, String title, String prompt, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial == null ? "" : initial);
        prepare(dialog, owner, title);
        dialog.setHeaderText(prompt);
        dialog.setGraphic(null);
        dialog.getEditor().setPrefColumnCount(28);
        Optional<String> answer = dialog.showAndWait();
        return answer.isPresent() ? answer.get() : null;
    }

    /** Shows a monospaced text, e.g. a receipt or an integrity report, with an optional extra button. */
    public static void showText(Window owner, String title, String text, String extraButton, UiAction extraAction) {
        Dialog<ButtonType> dialog = new Dialog<>();
        prepare(dialog, owner, title);
        dialog.setHeaderText(title);
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.getStyleClass().add("mono");
        area.setPrefSize(460, 460);
        dialog.getDialogPane().setContent(area);
        ButtonType extra = null;
        if (extraButton != null) {
            extra = new ButtonType(extraButton, ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().add(extra);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (extra != null) {
            Button extraNode = (Button) dialog.getDialogPane().lookupButton(extra);
            extraNode.setGraphic(new Icon("download", 15));
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
        dialog.getDialogPane().getStylesheets().add(Dialogs.class.getResource(CSS).toExternalForm());
        if (dialog instanceof Alert) {
            ((Alert) dialog).setHeaderText(null);
        }
        // tables inside dialogs share the width like the tables on the screens
        dialog.setOnShown(event -> UiUtil.fitTables(dialog.getDialogPane()));
    }

    /**
     * Replaces the platform alert layout with: toned icon | heading, optional emphasised line, message.
     * Call after the header text (if any) was set; it becomes the emphasised line.
     */
    private static void decorate(Alert alert, String heading, Badge.Tone tone, String iconName) {
        String emphasis = alert.getHeaderText();
        String message = alert.getContentText();
        alert.setHeaderText(null);
        alert.setGraphic(null);
        HBox box = new HBox(14, toneIcon(tone, iconName), textBlock(heading, emphasis, message));
        box.setMinWidth(380);
        box.setPrefWidth(440);
        alert.getDialogPane().setContent(box);
        alert.getDialogPane().getStyleClass().add("alert-" + tone.name().toLowerCase());
    }

    private static StackPane toneIcon(Badge.Tone tone, String iconName) {
        StackPane icon = new StackPane(new Icon(iconName, 20));
        icon.getStyleClass().addAll("alert-icon", tone.styleClass());
        return icon;
    }

    private static VBox textBlock(String heading, String emphasis, String message) {
        VBox text = new VBox(6);
        Label title = new Label(TextUtil.isBlank(heading) ? "Pan-ya Ya" : heading);
        title.getStyleClass().add("alert-heading");
        title.setWrapText(true);
        text.getChildren().add(title);
        if (!TextUtil.isBlank(emphasis)) {
            Label line = new Label(emphasis);
            line.getStyleClass().add("alert-detail-line");
            line.setWrapText(true);
            text.getChildren().add(line);
        }
        if (!TextUtil.isBlank(message)) {
            Label body = new Label(message);
            body.getStyleClass().add("alert-message");
            body.setWrapText(true);
            body.setMinHeight(Label.USE_PREF_SIZE);
            text.getChildren().add(body);
        }
        HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
        return text;
    }
}
