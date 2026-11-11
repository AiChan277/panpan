package app.panyaya.ui.common;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Calm "nothing here yet" message with an icon, used for empty tables, lists and charts. */
public class EmptyState extends VBox {

    public EmptyState(String iconName, String title, String message) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        setFillWidth(false);
        if (iconName != null) {
            StackPane icon = new StackPane(new Icon(iconName, 22));
            icon.getStyleClass().add("empty-state-icon");
            getChildren().add(icon);
        }
        if (title != null) {
            Label heading = new Label(title);
            heading.getStyleClass().add("empty-state-title");
            heading.setWrapText(true);
            getChildren().add(heading);
        }
        if (message != null) {
            Label text = new Label(message);
            text.getStyleClass().add("empty-state-text");
            text.setWrapText(true);
            text.setMaxWidth(380);
            getChildren().add(text);
        }
    }
}
