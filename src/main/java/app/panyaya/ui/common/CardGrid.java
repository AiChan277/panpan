package app.panyaya.ui.common;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

import java.util.List;

/**
 * Responsive grid for cards and panels: the number of columns follows the available width
 * (never narrower than {@code minColumnWidth}, never more than {@code maxColumns}). All cells in a
 * row get the same height, and an incomplete last row is stretched across the full width so the
 * grid never shows holes. Usable from FXML: {@code <CardGrid minColumnWidth="260" maxColumns="3"/>}.
 */
public class CardGrid extends Pane {

    private final DoubleProperty minColumnWidth = new SimpleDoubleProperty(this, "minColumnWidth", 220);
    private final IntegerProperty maxColumns = new SimpleIntegerProperty(this, "maxColumns", 4);
    private final DoubleProperty hgap = new SimpleDoubleProperty(this, "hgap", 12);
    private final DoubleProperty vgap = new SimpleDoubleProperty(this, "vgap", 12);
    private final DoubleProperty cornerRadius = new SimpleDoubleProperty(this, "cornerRadius", 0);
    private double[] weights = new double[0];

    public CardGrid() {
        getStyleClass().add("card-grid");
        minColumnWidth.addListener((obs, old, value) -> requestLayout());
        maxColumns.addListener((obs, old, value) -> requestLayout());
        hgap.addListener((obs, old, value) -> requestLayout());
        vgap.addListener((obs, old, value) -> requestLayout());
        cornerRadius.addListener((obs, old, value) -> updateClip());
    }

    public final double getMinColumnWidth() {
        return minColumnWidth.get();
    }

    public final void setMinColumnWidth(double value) {
        minColumnWidth.set(value);
    }

    public final int getMaxColumns() {
        return maxColumns.get();
    }

    public final void setMaxColumns(int value) {
        maxColumns.set(value);
    }

    public final double getHgap() {
        return hgap.get();
    }

    public final void setHgap(double value) {
        hgap.set(value);
    }

    public final double getVgap() {
        return vgap.get();
    }

    public final void setVgap(double value) {
        vgap.set(value);
    }

    /** Rounds the corners of the whole grid (used when the gaps act as divider lines). */
    public final double getCornerRadius() {
        return cornerRadius.get();
    }

    public final void setCornerRadius(double value) {
        cornerRadius.set(value);
    }

    /**
     * Relative widths for full rows, e.g. "3,2" makes the first of two columns 60 % wide. Only used
     * while the grid really shows that many columns; stacked layouts stay full width.
     */
    public final String getColumnWeights() {
        StringBuilder text = new StringBuilder();
        for (double weight : weights) {
            text.append(text.length() > 0 ? "," : "").append(weight);
        }
        return text.toString();
    }

    public final void setColumnWeights(String value) {
        String[] parts = value == null || value.isBlank() ? new String[0] : value.split(",");
        weights = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            weights[i] = Math.max(0.1, Double.parseDouble(parts[i].trim()));
        }
        requestLayout();
    }

    private void updateClip() {
        if (getCornerRadius() <= 0) {
            setClip(null);
            return;
        }
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(getCornerRadius() * 2);
        clip.setArcHeight(getCornerRadius() * 2);
        setClip(clip);
    }

    private int columnsFor(double contentWidth, int count) {
        if (count == 0) {
            return 1;
        }
        int columns = (int) Math.floor((contentWidth + getHgap()) / (getMinColumnWidth() + getHgap()));
        return Math.max(1, Math.min(columns, Math.min(Math.max(1, getMaxColumns()), count)));
    }

    /** Widths of the cells of one row: weighted when configured, otherwise equal. */
    private double[] cellWidths(double contentWidth, int cellsInRow, int columns) {
        double free = Math.max(0, contentWidth - getHgap() * (cellsInRow - 1));
        double[] widths = new double[cellsInRow];
        boolean weighted = weights.length == cellsInRow && cellsInRow == columns && cellsInRow > 1;
        double total = 0;
        for (int i = 0; i < cellsInRow; i++) {
            total += weighted ? weights[i] : 1;
        }
        for (int i = 0; i < cellsInRow; i++) {
            widths[i] = free * (weighted ? weights[i] : 1) / total;
        }
        return widths;
    }

    @Override
    protected double computeMinWidth(double height) {
        Insets in = getInsets();
        return in.getLeft() + in.getRight() + Math.min(getMinColumnWidth(), 160);
    }

    @Override
    protected double computePrefWidth(double height) {
        Insets in = getInsets();
        int count = getManagedChildren().size();
        int columns = Math.max(1, Math.min(getMaxColumns(), count));
        return in.getLeft() + in.getRight() + columns * getMinColumnWidth() + (columns - 1) * getHgap();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets in = getInsets();
        double total = width > 0 ? width : (getWidth() > 0 ? getWidth() : computePrefWidth(-1));
        double contentWidth = total - in.getLeft() - in.getRight();
        List<Node> cells = getManagedChildren();
        int columns = columnsFor(contentWidth, cells.size());
        double height = 0;
        for (int start = 0; start < cells.size(); start += columns) {
            int end = Math.min(start + columns, cells.size());
            double[] w = cellWidths(contentWidth, end - start, columns);
            double rowHeight = 0;
            for (int i = start; i < end; i++) {
                rowHeight = Math.max(rowHeight, cells.get(i).prefHeight(w[i - start]));
            }
            height += rowHeight + (start > 0 ? getVgap() : 0);
        }
        return in.getTop() + in.getBottom() + height;
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double contentWidth = getWidth() - in.getLeft() - in.getRight();
        List<Node> cells = getManagedChildren();
        int columns = columnsFor(contentWidth, cells.size());
        double y = in.getTop();
        for (int start = 0; start < cells.size(); start += columns) {
            int end = Math.min(start + columns, cells.size());
            double[] w = cellWidths(contentWidth, end - start, columns);
            double rowHeight = 0;
            for (int i = start; i < end; i++) {
                rowHeight = Math.max(rowHeight, cells.get(i).prefHeight(w[i - start]));
            }
            double x = in.getLeft();
            for (int i = start; i < end; i++) {
                double width = w[i - start];
                cells.get(i).resizeRelocate(snapPositionX(x), snapPositionY(y), snapSizeX(width), snapSizeY(rowHeight));
                x += width + getHgap();
            }
            y += rowHeight + getVgap();
        }
    }
}
