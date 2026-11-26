package app.panyaya.ui.common;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * One key figure on the dashboard: label and icon, the value in large tabular type, and a line of
 * supporting information. The supporting line can carry a tone (e.g. warning when something is sold
 * out); it is only ever filled with figures computed from the bakery's own data.
 */
public class KpiCard extends VBox {

    private final Label note = new Label();

    public KpiCard(String iconName, String label, String value, String noteText) {
        getStyleClass().add("kpi-card");

        Label caption = new Label(label);
        caption.getStyleClass().add("kpi-label");
        caption.setWrapText(true);
        StackPane icon = new StackPane(new Icon(iconName, 18));
        icon.getStyleClass().add("kpi-icon");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, caption, spacer, icon);
        header.getStyleClass().add("kpi-header");

        Label number = new Label(value);
        number.getStyleClass().add("kpi-value");
        number.setMinWidth(Region.USE_PREF_SIZE);

        note.setText(noteText);
        note.getStyleClass().add("kpi-note");
        note.setWrapText(true);

        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        getChildren().addAll(header, number, push, note);
    }

    /** Colours the supporting line; the text itself always explains the state. */
    public KpiCard tone(Badge.Tone tone) {
        if (tone != null) {
            note.getStyleClass().add(tone.styleClass());
        }
        return this;
    }

    /** Adds a small figure (e.g. "+12 % vs. yesterday") in front of the supporting line. */
    public KpiCard delta(String text, Badge.Tone tone) {
        if (text != null) {
            Label delta = new Label(text);
            delta.getStyleClass().addAll("kpi-delta", tone.styleClass());
            note.setGraphic(delta);
        }
        return this;
    }
}
