package app.panyaya.ui.common;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Builds simple form dialogs: label + field rows, an inline error label and an OK button that only
 * closes the dialog when the save succeeded. If saving fails, the message appears in red and the
 * user's input is kept, so nothing has to be typed again.
 */
public class FormDialog {

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final GridPane grid = new GridPane();
    private final Label errorLabel = new Label();
    private final ButtonType okType;
    private int row;

    public FormDialog(Window owner, String title, String okText) {
        Dialogs.prepare(dialog, owner, title);
        okType = new ButtonType(okText, javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(150);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setMinWidth(320);
        grid.getColumnConstraints().addAll(labels, fields);

        errorLabel.getStyleClass().add("form-error");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(520);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("form-scroll");
        VBox content = new VBox(12, scroll, errorLabel);
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);
    }

    /** Adds a labelled row and returns the control, so it can be created inline. */
    public <C extends Node> C add(String label, C control) {
        Label caption = new Label(label);
        caption.getStyleClass().add("form-label");
        grid.add(caption, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        row++;
        return control;
    }

    /** A row spanning both columns (tables, hints, section titles). */
    public <C extends Node> C addWide(C node) {
        grid.add(node, 0, row, 2, 1);
        row++;
        return node;
    }

    public Label addHint(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        return addWide(hint);
    }

    public void setWidth(double width) {
        ((VBox) dialog.getDialogPane().getContent()).setPrefWidth(width);
        errorLabel.setMaxWidth(width - 40);
    }

    public Dialog<ButtonType> getDialog() {
        return dialog;
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    /**
     * Shows the dialog. When OK is pressed, {@code onOk} runs; if it throws, the error is shown and
     * the dialog stays open.
     *
     * @return true if the dialog was closed with a successful OK
     */
    public boolean show(UiAction onOk) {
        Button ok = (Button) dialog.getDialogPane().lookupButton(okType);
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                onOk.run();
            } catch (Exception e) {
                showError(Dialogs.describe(e));
                event.consume(); // keep the dialog open with the user's input
            }
        });
        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == okType;
    }
}
