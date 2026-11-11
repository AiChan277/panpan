package app.panyaya.ui.common;

import app.panyaya.util.TextUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

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

    /**
     * Status column: each value is drawn as a {@link Badge} whose tone follows the status word
     * ("Active", "Low", "Expired"...). Sorting still works on the text.
     */
    public static <T> TableColumn<T, String> badgeColumn(String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = column(title, width, value);
        column.getStyleClass().add("badge-column");
        column.setCellFactory(c -> new TableCell<T, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || TextUtil.isBlank(item) || "-".equals(item)) {
                    setGraphic(null);
                    setText(empty ? null : item);
                } else {
                    setGraphic(new Badge(item, Badge.toneOf(item)));
                }
            }
        });
        return column;
    }

    /** Gives cells a text style class (e.g. "text-danger") whenever {@code when} matches the value. */
    public static <T> TableColumn<T, String> highlight(TableColumn<T, String> column, Predicate<String> when,
                                                      String styleClass) {
        column.setCellFactory(c -> new TableCell<T, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().remove(styleClass);
                if (!empty && item != null && when.test(item)) {
                    getStyleClass().add(styleClass);
                }
            }
        });
        return column;
    }

    /**
     * Shows a short status message in a label as a toned banner with an icon (success, warning,
     * danger, info). An empty text hides the label.
     */
    public static void notice(Label label, Badge.Tone tone, String text) {
        label.getStyleClass().removeAll("banner-success", "banner-warning", "banner-danger", "banner-info");
        boolean show = !TextUtil.isBlank(text);
        label.setText(show ? text : "");
        label.setVisible(show);
        label.setManaged(show);
        if (!show) {
            return;
        }
        String icon;
        switch (tone) {
            case SUCCESS:
                label.getStyleClass().add("banner-success");
                icon = "check-circle";
                break;
            case WARNING:
                label.getStyleClass().add("banner-warning");
                icon = "alert";
                break;
            case DANGER:
                label.getStyleClass().add("banner-danger");
                icon = "x-circle";
                break;
            default:
                label.getStyleClass().add("banner-info");
                icon = "info";
                break;
        }
        label.setGraphic(new Icon(icon, 16));
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
    }

    /** Empty table/list message for "all good" situations (green check instead of the inbox icon). */
    public static EmptyState allClear(String text) {
        EmptyState state = new EmptyState("check", null, text);
        state.getStyleClass().add("success");
        return state;
    }

    public static void placeholder(TableView<?> table, String text) {
        table.setPlaceholder(new EmptyState("inbox", null, text));
    }

    /**
     * Lets the columns share the table width (no horizontal scroll bar while they fit) and keeps
     * every column readable when the window gets narrow.
     */
    public static void fitTable(TableView<?> table) {
        if (table.getProperties().containsKey("panyaya.fitted")) {
            return;
        }
        table.getProperties().put("panyaya.fitted", Boolean.TRUE);
        if (table.getPlaceholder() == null) {
            table.setPlaceholder(new EmptyState("inbox", null, "Nothing to show yet."));
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().forEach(UiUtil::readableMinimum);
        table.getColumns().addListener((ListChangeListener<TableColumn<?, ?>>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(UiUtil::readableMinimum);
            }
        });
    }

    /** Applies {@link #fitTable} to every table inside {@code root} (call after the node has a skin). */
    public static void fitTables(Node root) {
        for (Node node : root.lookupAll(".table-view")) {
            if (node instanceof TableView) {
                fitTable((TableView<?>) node);
            }
        }
    }

    /**
     * Page toolbars switch their secondary buttons to icon-only (the tooltip keeps the name) when the
     * labelled buttons would not fit, instead of cutting their labels off with "...". The toolbar
     * itself may shrink (that is how a lack of room is detected); the primary button keeps its label.
     */
    public static void compactToolbars(Node root) {
        for (Node node : root.lookupAll(".toolbar")) {
            if (node instanceof HBox && !node.getProperties().containsKey("panyaya.compact")) {
                HBox bar = (HBox) node;
                bar.getProperties().put("panyaya.compact", Boolean.TRUE);
                bar.setMinWidth(0);
                bar.widthProperty().addListener((obs, old, width) -> updateCompact(bar));
            }
        }
    }

    private static void updateCompact(HBox bar) {
        double width = bar.getWidth();
        if (width <= 0) {
            return;
        }
        if (!bar.getStyleClass().contains("compact")) {
            double needed = neededWidth(bar);
            if (needed > width + 0.5) {
                bar.getProperties().put("panyaya.fullWidth", needed);
                bar.getStyleClass().add("compact");
            }
        } else if (width >= (Double) bar.getProperties().getOrDefault("panyaya.fullWidth", 0.0)) {
            bar.getStyleClass().remove("compact");
        }
    }

    /** Width the toolbar needs with labelled buttons and every other control at its minimum. */
    private static double neededWidth(HBox bar) {
        double total = bar.getInsets().getLeft() + bar.getInsets().getRight();
        int count = 0;
        for (Node child : bar.getChildren()) {
            if (!child.isManaged()) {
                continue;
            }
            total += child instanceof Button ? child.prefWidth(-1) : child.minWidth(-1);
            count++;
        }
        return total + bar.getSpacing() * Math.max(0, count - 1);
    }

    /** Amounts and quantities keep almost their full width; text columns give way first. */
    private static void readableMinimum(TableColumn<?, ?> column) {
        if (column.getStyleClass().contains("number-column")) {
            column.setMinWidth(Math.max(64, column.getPrefWidth() * 0.85));
        } else {
            column.setMinWidth(Math.max(56, Math.min(column.getPrefWidth() * 0.5, 140)));
        }
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

    /**
     * Status word for a table cell. Status columns are drawn as {@link Badge}s (see
     * {@link #badgeColumn}), which add the colour; the word itself is always shown.
     */
    public static String statusText(String status) {
        return status;
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
