package app.panyaya.ui.common;

import app.panyaya.util.TextUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Small helpers that keep the controllers short, mainly for building table columns.
 *
 * <p>Course topic <b>Generics</b>: {@code column("Name", Product::getName)} works for a table of any
 * type {@code T}; the {@link Function} turns a row object into the text of the cell.</p>
 */
public final class UiUtil {

    /** Sorts "Rp 18.000", "1.25" and "12" by their numeric value instead of alphabetically. */
    public static final Comparator<String> NUMERIC = (a, b) -> toNumber(a).compareTo(toNumber(b));

    private UiUtil() {
    }

    public static <T> TableColumn<T, String> column(String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /** Right-aligned column for amounts and quantities, sorted numerically. */
    public static <T> TableColumn<T, String> numberColumn(String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = column(title, width, value);
        column.getStyleClass().add("number-column");
        column.setComparator(NUMERIC);
        return column;
    }

    public static void placeholder(TableView<?> table, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        label.setWrapText(true);
        table.setPlaceholder(label);
    }

    /** Replaces the table content while keeping the selected row (matched by equals = same id). */
    public static <T> void setItems(TableView<T> table, List<T> items) {
        T selected = table.getSelectionModel().getSelectedItem();
        table.setItems(FXCollections.observableArrayList(items));
        if (selected != null) {
            int index = items.indexOf(selected);
            if (index >= 0) {
                table.getSelectionModel().select(index);
            }
        }
    }

    public static <T> void setChoices(ComboBox<T> box, List<T> items) {
        T selected = box.getValue();
        box.setItems(FXCollections.observableArrayList(items));
        if (selected != null && items.contains(selected)) {
            box.setValue(selected);
        }
    }

    /** Filter combo boxes contain a {@code null} entry meaning "all"; this shows it with a label. */
    public static <T> void allOption(ComboBox<T> box, String allLabel, List<T> choices) {
        box.setConverter(new javafx.util.StringConverter<T>() {
            @Override
            public String toString(T value) {
                return value == null ? allLabel : value.toString();
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
        java.util.List<T> items = new java.util.ArrayList<>();
        items.add(null);
        items.addAll(choices);
        T selected = box.getValue();
        box.setItems(FXCollections.observableArrayList(items));
        box.setValue(selected != null && choices.contains(selected) ? selected : null);
    }

    public static TextField field(String prompt, String value) {
        TextField field = new TextField(value == null ? "" : value);
        field.setPromptText(prompt);
        return field;
    }

    public static TextArea area(String prompt, String value, int rows) {
        TextArea area = new TextArea(value == null ? "" : value);
        area.setPromptText(prompt);
        area.setPrefRowCount(rows);
        area.setWrapText(true);
        return area;
    }

    /** Status text with a symbol, so status is never shown by colour alone. */
    public static String statusText(String status) {
        switch (status) {
            case "OK":
            case "Active":
            case "Completed":
                return "✓ " + status;          // check mark
            case "Low":
            case "Draft":
            case "Planned":
                return "⚠ " + status;          // warning sign
            case "Out of stock":
            case "Failed":
            case "Voided":
            case "Expired":
                return "✖ " + status;          // heavy x
            default:
                return status;
        }
    }

    private static BigDecimal toNumber(String text) {
        if (TextUtil.isBlank(text)) {
            return BigDecimal.valueOf(Long.MIN_VALUE);
        }
        String value = text.trim();
        boolean negative = value.startsWith("-");
        if (value.contains("Rp")) {
            // Indonesian format: '.' groups thousands, ',' is the decimal mark
            value = value.replace("Rp", "").replace("-", "").replace(".", "").replace(",", ".").trim();
        }
        StringBuilder digits = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c) || c == '.') {
                digits.append(c);
            } else if (c == ' ') {
                break; // "12 g" -> 12
            }
        }
        try {
            BigDecimal number = new BigDecimal(digits.toString());
            return negative ? number.negate() : number;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
