package app.panyaya.ui.common;

import javafx.scene.control.Label;
import javafx.scene.shape.Circle;

import java.util.Locale;

/**
 * A small status pill: a coloured dot plus the status word. The word is always shown, so status is
 * never communicated by colour alone. The tone has the same meaning on every screen:
 * success = fine / done, warning = needs attention soon, danger = act now / failed,
 * neutral = inactive or historical, info = in progress.
 */
public final class Badge extends Label {

    public enum Tone {
        SUCCESS("tone-success"),
        WARNING("tone-warning"),
        DANGER("tone-danger"),
        INFO("tone-info"),
        NEUTRAL("tone-neutral");

        private final String styleClass;

        Tone(String styleClass) {
            this.styleClass = styleClass;
        }

        public String styleClass() {
            return styleClass;
        }
    }

    public Badge(String text, Tone tone) {
        super(text);
        Circle dot = new Circle(3);
        dot.getStyleClass().add("badge-dot");
        setGraphic(dot);
        getStyleClass().addAll("badge", tone.styleClass());
        setMinWidth(USE_PREF_SIZE);
    }

    /** Maps the status words used by the application to a tone. Unknown words are neutral. */
    public static Tone toneOf(String status) {
        if (status == null) {
            return Tone.NEUTRAL;
        }
        String s = status.toLowerCase(Locale.ROOT);
        if (s.startsWith("out of stock") || s.startsWith("failed") || s.startsWith("voided")
                || s.startsWith("expired") || s.startsWith("short")) {
            return Tone.DANGER;
        }
        if (s.startsWith("low") || s.startsWith("draft") || s.startsWith("expiring")) {
            return Tone.WARNING;
        }
        if (s.startsWith("in progress") || s.startsWith("planned") || s.startsWith("open")) {
            return Tone.INFO;
        }
        if (s.equals("ok") || s.startsWith("active") || s.startsWith("completed") || s.startsWith("usable")
                || s.startsWith("enough") || s.startsWith("used (fefo)")) {
            return Tone.SUCCESS;
        }
        return Tone.NEUTRAL;
    }
}
