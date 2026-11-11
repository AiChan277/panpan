package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.model.BakeryData;
import app.panyaya.model.user.Permission;
import app.panyaya.model.user.Role;
import app.panyaya.model.user.User;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.Icon;
import app.panyaya.ui.common.Shell;
import app.panyaya.ui.common.TaskRunner;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.ui.common.ViewController;
import app.panyaya.util.AppLog;
import app.panyaya.util.Formatters;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The main window: sidebar navigation (filtered by the user's role), page header, content area and
 * status bar. Screens are loaded from FXML the first time they are opened and then reused.
 *
 * <p>The layout adapts to the window: below {@value #COLLAPSE_BELOW} px the sidebar shrinks to its
 * icons, and page toolbars that run out of room switch their secondary buttons to icon-only.</p>
 */
public class MainController implements Shell {

    /** Sidebar shrinks to icons when the window is narrower than this. */
    private static final double COLLAPSE_BELOW = 1240;
    private static final double SIDEBAR_WIDTH = 236;
    private static final double SIDEBAR_COLLAPSED = 68;
    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);

    /** One entry of the sidebar. */
    private static class NavItem {
        final String id;
        final String section;
        final String label;
        final String icon;
        final String description;
        final Permission permission;

        NavItem(String id, String section, String label, String icon, String description, Permission permission) {
            this.id = id;
            this.section = section;
            this.label = label;
            this.icon = icon;
            this.description = description;
            this.permission = permission;
        }
    }

    private static final List<NavItem> NAVIGATION = new ArrayList<>();

    static {
        NAVIGATION.add(new NavItem("Dashboard", "Overview", "Dashboard", "dashboard",
                "Today's sales, production, waste and the stock that needs attention.", Permission.VIEW_DASHBOARD));
        NAVIGATION.add(new NavItem("Products", "Management", "Products", "products",
                "Product catalogue, selling prices, categories and sellable stock.", Permission.VIEW_PRODUCTS));
        NAVIGATION.add(new NavItem("Recipes", "Management", "Recipes", "recipes",
                "Recipes with ingredient quantities, material cost estimates and a scale calculator.",
                Permission.VIEW_RECIPES));
        NAVIGATION.add(new NavItem("Ingredients", "Management", "Ingredients", "ingredients",
                "Ingredient master data, usable stock against minimums, and lots (earliest expiry used first).",
                Permission.VIEW_INVENTORY));
        NAVIGATION.add(new NavItem("Inventory", "Management", "Inventory & Receiving", "inventory",
                "Every stock change is a ledger entry: receipts, production use, sales, voids, waste and adjustments.",
                Permission.VIEW_INVENTORY));
        NAVIGATION.add(new NavItem("Suppliers", "Management", "Suppliers", "supplier",
                "Supplier records and their delivery history.", Permission.MANAGE_SUPPLIERS));
        NAVIGATION.add(new NavItem("Production", "Operations", "Production", "production",
                "Plan, start and complete bakes. Ingredients are taken from stock when a batch starts.",
                Permission.MANAGE_PRODUCTION));
        NAVIGATION.add(new NavItem("Pos", "Operations", "Point of Sale", "pointofsale",
                "Sell products, take payment and issue receipts. Only stock that has not expired can be sold.",
                Permission.USE_POS));
        NAVIGATION.add(new NavItem("SalesHistory", "Operations", "Sales History", "saleshistory",
                "Completed and voided sales with their receipts.", Permission.USE_POS));
        NAVIGATION.add(new NavItem("Waste", "Operations", "Waste Management", "wastemanagement",
                "Record waste, write off expiring batches at the end of the day, and review losses.",
                Permission.RECORD_PRODUCT_WASTE));
        NAVIGATION.add(new NavItem("Reports", "Analytics", "Reports", "reports",
                "Sales, production, waste and stock reports for any period, with CSV export.", Permission.VIEW_REPORTS));
        NAVIGATION.add(new NavItem("Users", "System", "User Management", "usermanagement",
                "Each employee signs in with their own account, so every sale and stock change shows who did it.",
                Permission.MANAGE_USERS));
        NAVIGATION.add(new NavItem("Settings", "System", "Settings", "settings",
                "Bakery profile, alert thresholds, CSV import and export, and data checks.", Permission.MANAGE_SETTINGS));
        NAVIGATION.add(new NavItem("Backup", "System", "Backup & Restore", "database",
                "Create, verify and restore backups of the bakery data.", Permission.MANAGE_BACKUP));
    }

    @FXML
    private VBox sidebar;
    @FXML
    private HBox brandBox;
    @FXML
    private VBox brandText;
    @FXML
    private VBox navBox;
    @FXML
    private Label avatarLabel;
    @FXML
    private VBox userText;
    @FXML
    private FlowPane userActions;
    @FXML
    private Button passwordButton;
    @FXML
    private Button signOutButton;
    @FXML
    private Label userLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label pageTitle;
    @FXML
    private Label pageDescription;
    @FXML
    private Label crumbSection;
    @FXML
    private Label bakeryLabel;
    @FXML
    private Label headerDate;
    @FXML
    private Button alertsButton;
    @FXML
    private Label alertCount;
    @FXML
    private Tooltip alertsTooltip;
    @FXML
    private StackPane contentPane;
    @FXML
    private Label statusLabel;
    @FXML
    private Label saveLabel;
    @FXML
    private ProgressIndicator busyIndicator;

    private AppContext ctx;
    private TaskRunner taskRunner;
    private Stage stage;
    private Runnable onSignedOut;
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<String, ToggleButton> navButtons = new HashMap<>();
    private final Map<String, NavItem> navItems = new HashMap<>();
    private final List<Label> sectionLabels = new ArrayList<>();
    private final Map<String, Parent> loadedViews = new HashMap<>();
    private final Map<String, ViewController> controllers = new HashMap<>();
    private final Runnable saveListener = this::onDataSaved;
    private String currentView;
    private boolean collapsed;
    private Timeline sidebarAnimation;

    public void start(AppContext context, TaskRunner tasks, Stage window, Runnable signOutCallback) {
        this.ctx = context;
        this.taskRunner = tasks;
        this.stage = window;
        this.onSignedOut = signOutCallback;
        taskRunner.setBusyIndicator(busy -> busyIndicator.setVisible(busy));

        User user = ctx.getSession().getUser();
        userLabel.setText(user.getDisplayName());
        roleLabel.setText(user.getRole() + "  ·  @" + user.getUsername());
        avatarLabel.setText(initials(user.getDisplayName()));
        bakeryLabel.setText(ctx.getStore().getData().getSettings().getBakeryName());
        headerDate.setText(HEADER_DATE.format(LocalDate.now(ctx.settings().getSettings().zone())));
        buildNavigation();
        setUpResponsiveLayout();
        boolean alerts = ctx.getSession().can(Permission.VIEW_DASHBOARD);
        alertsButton.setVisible(alerts);
        alertsButton.setManaged(alerts);
        ctx.getStore().addChangeListener(saveListener);
        onDataSaved();

        // role-specific home screen
        if (user.getRole() == Role.CASHIER) {
            showView("Pos");
        } else if (user.getRole() == Role.BAKER) {
            showView("Production");
        } else {
            showView("Dashboard");
        }
        startDailyBackup();
    }

    private static String initials(String name) {
        StringBuilder letters = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0)) && letters.length() < 2) {
                letters.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return letters.length() == 0 ? "?" : letters.toString();
    }

    private void buildNavigation() {
        // LinkedHashMap keeps the sections in the order they were defined
        Map<String, List<NavItem>> sections = new LinkedHashMap<>();
        for (NavItem item : NAVIGATION) {
            if (ctx.getSession().can(item.permission)) {
                sections.computeIfAbsent(item.section, key -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<String, List<NavItem>> section : sections.entrySet()) {
            Label header = new Label(section.getKey().toUpperCase(Locale.ROOT));
            header.getStyleClass().add("nav-section");
            header.setMaxWidth(Double.MAX_VALUE);
            header.getProperties().put("text", header.getText());
            sectionLabels.add(header);
            navBox.getChildren().add(header);
            for (NavItem item : section.getValue()) {
                ToggleButton button = new ToggleButton(item.label, new Icon(item.icon, 18));
                button.getStyleClass().setAll("nav-item");
                button.setToggleGroup(navGroup);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setMnemonicParsing(false);
                button.setOnAction(e -> showView(item.id));
                navButtons.put(item.id, button);
                navItems.put(item.id, item);
                navBox.getChildren().add(button);
            }
        }
        // a selected entry stays selected when it is clicked again
        navGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
    }

    /** Collapses the sidebar on narrow windows (page toolbars compact themselves, see UiUtil). */
    private void setUpResponsiveLayout() {
        sidebar.setMinWidth(Region.USE_PREF_SIZE);
        sidebar.setMaxWidth(Region.USE_PREF_SIZE);
        double width = stage.getWidth() > 0 ? stage.getWidth() : contentPane.getScene().getWidth();
        setCollapsed(width < COLLAPSE_BELOW, false);
        contentPane.getScene().widthProperty().addListener((obs, old, now) -> {
            boolean wasNarrow = old.doubleValue() < COLLAPSE_BELOW;
            boolean isNarrow = now.doubleValue() < COLLAPSE_BELOW;
            if (wasNarrow != isNarrow) {
                setCollapsed(isNarrow, true); // only when crossing the threshold, so a manual choice sticks
            }
        });
    }

    private void setCollapsed(boolean value, boolean animate) {
        collapsed = value;
        if (value) {
            if (!sidebar.getStyleClass().contains("collapsed")) {
                sidebar.getStyleClass().add("collapsed");
            }
        } else {
            sidebar.getStyleClass().remove("collapsed");
        }
        brandText.setVisible(!value);
        brandText.setManaged(!value);
        userText.setVisible(!value);
        userText.setManaged(!value);
        for (Label header : sectionLabels) {
            header.setText(value ? "" : (String) header.getProperties().get("text"));
        }
        for (Map.Entry<String, ToggleButton> entry : navButtons.entrySet()) {
            ToggleButton button = entry.getValue();
            button.setContentDisplay(value ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            button.setTooltip(value ? new Tooltip(navItems.get(entry.getKey()).label) : null);
        }
        for (Button button : new Button[]{passwordButton, signOutButton}) {
            button.setContentDisplay(value ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        }
        double target = value ? SIDEBAR_COLLAPSED : SIDEBAR_WIDTH;
        if (sidebarAnimation != null) {
            sidebarAnimation.stop();
        }
        if (animate && sidebar.getPrefWidth() > 0) {
            sidebarAnimation = new Timeline(new KeyFrame(Duration.millis(160),
                    new KeyValue(sidebar.prefWidthProperty(), target)));
            sidebarAnimation.play();
        } else {
            sidebar.setPrefWidth(target);
        }
    }

    @FXML
    private void onToggleSidebar() {
        setCollapsed(!collapsed, true);
    }

    @Override
    public void showView(String viewId) {
        ToggleButton button = navButtons.get(viewId);
        if (button == null) {
            return; // not allowed for this role
        }
        button.setSelected(true);
        try {
            Parent view = loadedViews.get(viewId);
            boolean firstTime = view == null;
            if (firstTime) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/app/panyaya/ui/" + viewId + "View.fxml"));
                view = loader.load();
                ViewController controller = loader.getController();
                controller.init(ctx, this);
                loadedViews.put(viewId, view);
                controllers.put(viewId, controller);
            }
            contentPane.getChildren().setAll(view);
            if (firstTime) {
                enhance(view);
            }
            NavItem item = navItems.get(viewId);
            pageTitle.setText(item.label);
            pageDescription.setText(item.description);
            crumbSection.setText(item.section);
            currentView = viewId;
            controllers.get(viewId).refresh();
        } catch (IOException | RuntimeException e) {
            AppLog.error("Cannot open view " + viewId, e);
            Dialogs.error(stage, e);
        }
    }

    /** Presentation touches shared by every screen: fitted table columns and tooltips for icon buttons. */
    private static void enhance(Parent view) {
        view.applyCss(); // creates the skins, so tables inside tabs can be found too
        UiUtil.fitTables(view);
        UiUtil.compactToolbars(view);
        for (Node node : view.lookupAll(".button")) {
            if (node instanceof Button) {
                Button button = (Button) node;
                if (button.getTooltip() == null && button.getGraphic() != null && !button.getText().isBlank()) {
                    button.setTooltip(new Tooltip(button.getText()));
                }
            }
        }
    }

    @FXML
    private void onRefresh() {
        ViewController controller = currentView == null ? null : controllers.get(currentView);
        if (controller != null) {
            controller.refresh();
            setStatus("Refreshed " + navItems.get(currentView).label);
        }
        refreshAlerts();
    }

    @FXML
    private void onAlerts() {
        showView("Dashboard");
    }

    /** Low-stock and expiry alerts, counted by the dashboard service (Owner only). */
    private void refreshAlerts() {
        if (!ctx.getSession().can(Permission.VIEW_DASHBOARD) || ctx.getSession().getUser() == null) {
            return;
        }
        taskRunner.run(() -> ctx.dashboard().build(), summary -> {
            int count = summary.getLowStock().size() + summary.getExpiringIngredients().size()
                    + summary.getExpiringProducts().size();
            alertCount.setText(count > 99 ? "99+" : String.valueOf(count));
            alertCount.setVisible(count > 0);
            alertsTooltip.setText(count == 0 ? "No stock alerts"
                    : count + " stock alert(s): " + summary.getLowStock().size() + " low stock, "
                    + (summary.getExpiringIngredients().size() + summary.getExpiringProducts().size())
                    + " expiring or expired");
        }, error -> AppLog.error("Cannot count stock alerts", error));
    }

    /** Keeps the status bar honest: it only says "saved" after the file was really replaced. */
    private void onDataSaved() {
        TaskRunner.onUiThread(() -> {
            BakeryData data = ctx.getStore().getData();
            saveLabel.setText("All changes saved  ·  " + Formatters.dateTime(data.getSavedAt(), data.getSettings().zone())
                    + "  ·  save #" + data.getSaveCount());
            refreshAlerts();
        });
    }

    /** Automatic daily backup in the background after sign-in (Owner data protection). */
    private void startDailyBackup() {
        taskRunner.run(() -> ctx.backups().createDailyBackupIfNeeded(), created -> {
            if (created != null) {
                setStatus("Daily backup created: " + created.getFileName());
            }
        }, error -> {
            AppLog.error("Daily backup failed", error);
            setStatus("Daily backup failed: " + error.getMessage());
        });
    }

    @FXML
    private void onChangePassword() {
        if (PasswordDialogs.change(stage, ctx)) {
            Dialogs.success(stage, "Password changed", "Your password was changed.");
        }
    }

    @FXML
    private void onSignOut() {
        if (currentView != null && "Pos".equals(currentView)
                && !Dialogs.confirm(stage, "Sign out", "Sign out? An unpaid cart will be discarded.")) {
            return;
        }
        signOutWithoutAsking();
    }

    @Override
    public void signOutWithoutAsking() {
        ctx.getStore().removeChangeListener(saveListener);
        ctx.auth().logout();
        onSignedOut.run();
    }

    @Override
    public TaskRunner tasks() {
        return taskRunner;
    }

    @Override
    public Window window() {
        return stage;
    }

    @Override
    public void setStatus(String message) {
        TaskRunner.onUiThread(() -> statusLabel.setText(message));
    }
}
