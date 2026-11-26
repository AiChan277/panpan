package app.panyaya.ui.common;

import app.panyaya.util.AppLog;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A vector icon from {@code /app/panyaya/ui/icons/<name>.svg} (24 x 24 view box), drawn with
 * {@link SVGPath} so it stays sharp at any size and takes its colour from CSS.
 *
 * <p>The SVG files are read from the classpath with an absolute resource path, so icons work no
 * matter which folder the application is started from. Missing icons are logged and simply drawn
 * empty; they never stop a screen from opening.</p>
 *
 * <p>Two drawing styles exist: the module icons from {@code Panyaya_res} are filled outlines
 * ({@code .icon-fill}); the small action icons are stroked lines ({@code .icon-line}). The CSS gives
 * both the same visual weight. Usable from FXML: {@code <Icon name="search" size="16"/>}.</p>
 */
public class Icon extends StackPane {

    private static final String BASE = "/app/panyaya/ui/icons/";
    private static final Pattern PATH = Pattern.compile("<path[^>]*?\\sd=\"([^\"]+)\"");
    private static final Map<String, Glyph> CACHE = new ConcurrentHashMap<>();

    /** Parsed SVG: the path data and whether it is drawn as lines or filled shapes. */
    private static final class Glyph {
        final List<String> paths;
        final boolean line;

        Glyph(List<String> paths, boolean line) {
            this.paths = paths;
            this.line = line;
        }
    }

    private static final Glyph EMPTY = new Glyph(Collections.emptyList(), false);

    private final StringProperty name = new SimpleStringProperty(this, "name");
    private final DoubleProperty size = new SimpleDoubleProperty(this, "size", 16);
    private final Group holder = new Group();

    public Icon() {
        getStyleClass().add("icon");
        setPickOnBounds(false);
        getChildren().add(holder);
        name.addListener((obs, old, value) -> rebuild());
        size.addListener((obs, old, value) -> rebuild());
        rebuild();
    }

    public Icon(String name) {
        this();
        setName(name);
    }

    public Icon(String name, double size) {
        this();
        setSize(size);
        setName(name);
    }

    public final StringProperty nameProperty() {
        return name;
    }

    public final String getName() {
        return name.get();
    }

    public final void setName(String value) {
        name.set(value);
    }

    public final DoubleProperty sizeProperty() {
        return size;
    }

    public final double getSize() {
        return size.get();
    }

    public final void setSize(double value) {
        size.set(value);
    }

    private void rebuild() {
        double side = getSize();
        setMinSize(side, side);
        setPrefSize(side, side);
        setMaxSize(side, side);
        Glyph glyph = glyph(getName());
        // a transparent 24 x 24 frame keeps the icon's own position inside its view box
        Rectangle frame = new Rectangle(24, 24, Color.TRANSPARENT);
        Group shape = new Group(frame);
        for (String data : glyph.paths) {
            SVGPath path = new SVGPath();
            path.setContent(data);
            path.getStyleClass().addAll("icon-shape", glyph.line ? "icon-line" : "icon-fill");
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            path.setStrokeLineJoin(StrokeLineJoin.ROUND);
            if (glyph.line) {
                path.setFill(null);
            }
            shape.getChildren().add(path);
        }
        shape.getTransforms().setAll(new Scale(side / 24.0, side / 24.0, 0, 0));
        holder.getChildren().setAll(shape);
    }

    private static Glyph glyph(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return EMPTY;
        }
        return CACHE.computeIfAbsent(iconName, Icon::load);
    }

    private static Glyph load(String iconName) {
        try (InputStream in = Icon.class.getResourceAsStream(BASE + iconName + ".svg")) {
            if (in == null) {
                AppLog.info("Icon not found: " + BASE + iconName + ".svg");
                return EMPTY;
            }
            String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> paths = new ArrayList<>();
            Matcher matcher = PATH.matcher(svg);
            while (matcher.find()) {
                paths.add(matcher.group(1));
            }
            boolean line = svg.contains("fill=\"none\"") && svg.contains("stroke=");
            return new Glyph(Collections.unmodifiableList(paths), line);
        } catch (IOException e) {
            AppLog.info("Icon cannot be read: " + iconName + " (" + e.getMessage() + ")");
            return EMPTY;
        }
    }
}
