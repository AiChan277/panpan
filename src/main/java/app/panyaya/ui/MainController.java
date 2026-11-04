package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.model.BakeryData;
import app.panyaya.model.user.Permission;
import app.panyaya.model.user.Role;
import app.panyaya.model.user.User;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.Shell;
import app.panyaya.ui.common.TaskRunner;
import app.panyaya.ui.common.ViewController;
import app.panyaya.util.AppLog;
import app.panyaya.util.Formatters;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The main window: sidebar navigation (filtered by the user's role), page title, content area and
 * status bar. Screens are loaded from FXML the first time they are opened and then reused.
 */
public class MainController implements Shell {

    /** One entry of the sidebar. */
    private static class NavItem {
        final String id;
        final String section;
        final String label;
        final Permission permission;

        NavItem(String id, String section, String label, Permission permission) {
            this.id = id;
            this.section = section;
            this.label = label;
            this.permission = permission;
        }
    }

    private static final List<NavItem> NAVIGATION = new ArrayList<>();

    static {
        NAVIGATION.add(new NavItem("Dashboard", "OVERVIEW", "Dashboard", Permission.VIEW_DASHBOARD));
        NAVIGATION.add(new NavItem("Products", "MANAGEMENT", "Products", Permission.VIEW_PRODUCTS));
        NAVIGATION.add(new NavItem("Recipes", "MANAGEMENT", "Recipes", Permission.VIEW_RECIPES));
        NAVIGATION.add(new NavItem("Ingredients", "MANAGEMENT", "Ingredients", Permission.VIEW_INVENTORY));
        NAVIGATION.add(new NavItem("Inventory", "MANAGEMENT", "Inventory & Receiving", Permission.VIEW_INVENTORY));
        NAVIGATION.add(new NavItem("Suppliers", "MANAGEMENT", "Suppliers", Permission.MANAGE_SUPPLIERS));
        NAVIGATION.add(new NavItem("Production", "OPERATIONS", "Production", Permission.MANAGE_PRODUCTION));
        NAVIGATION.add(new NavItem("Pos", "OPERATIONS", "Point of Sale", Permission.USE_POS));
        NAVIGATION.add(new NavItem("SalesHistory", "OPERATIONS", "Sales History", Permission.USE_POS));
        NAVIGATION.add(new NavItem("Waste", "OPERATIONS", "Waste Management", Permission.RECORD_PRODUCT_WASTE));
        NAVIGATION.add(new NavItem("Reports", "ANALYTICS", "Reports", Permission.VIEW_REPORTS));
        NAVIGATION.add(new NavItem("Users", "SYSTEM", "User Management", Permission.MANAGE_USERS));
        NAVIGATION.add(new NavItem("Settings", "SYSTEM", "Settings", Permission.MANAGE_SETTINGS));
        NAVIGATION.add(new NavItem("Backup", "SYSTEM", "Backup & Restore", Permission.MANAGE_BACKUP));
    }

    @FXML
    private VBox navBox;
    @FXML
    private Label userLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label pageTitle;
    @FXML
    private Label bakeryLabel;
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
    private final Map<String, Parent> loadedViews = new HashMap<>();
    private final Map<String, ViewController> controllers = new HashMap<>();
    private final Runnable saveListener = this::onDataSaved;
    private String currentView;

    public void start(AppContext context, TaskRunner tasks, Stage window, Runnable signOutCallback) {
        this.ctx = context;
        this.taskRunner = tasks;
        this.stage = window;
        this.onSignedOut = signOutCallback;
        taskRunner.setBusyIndicator(busy -> busyIndicator.setVisible(busy));

        User user = ctx.getSession().getUser();
        userLabel.setText(user.getDisplayName());
        roleLabel.setText(user.getRole() + " · @" + user.getUsername());
        bakeryLabel.setText(ctx.getStore().getData().getSettings().getBakeryName());
        buildNavigation();
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

    private void buildNavigation() {
        // LinkedHashMap keeps the sections in the order they were defined
        Map<String, List<NavItem>> sections = new LinkedHashMap<>();
        for (NavItem item : NAVIGATION) {
            if (ctx.getSession().can(item.permission)) {
                sections.computeIfAbsent(item.section, key -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<String, List<NavItem>> section : sections.entrySet()) {
            Label header = new Label(section.getKey());
            header.getStyleClass().add("nav-section");
            navBox.getChildren().add(header);
            for (NavItem item : section.getValue()) {
                ToggleButton button = new ToggleButton(item.label);
                button.getStyleClass().add("nav-button");
                button.setToggleGroup(navGroup);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setOnAction(e -> showView(item.id));
                navButtons.put(item.id, button);
                navBox.getChildren().add(button);
            }
        }
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
            if (view == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/app/panyaya/ui/" + viewId + "View.fxml"));
                view = loader.load();
                ViewController controller = loader.getController();
                controller.init(ctx, this);
                loadedViews.put(viewId, view);
                controllers.put(viewId, controller);
            }
            contentPane.getChildren().setAll(view);
            pageTitle.setText(button.getText());
            currentView = viewId;
            controllers.get(viewId).refresh();
        } catch (IOException | RuntimeException e) {
            AppLog.error("Cannot open view " + viewId, e);
            Dialogs.error(stage, e);
        }
    }

    /** Keeps the status bar honest: it only says "saved" after the file was really replaced. */
    private void onDataSaved() {
        TaskRunner.onUiThread(() -> {
            BakeryData data = ctx.getStore().getData();
            saveLabel.setText("All changes saved · " + Formatters.dateTime(data.getSavedAt(), data.getSettings().zone())
                    + " · save #" + data.getSaveCount());
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
            Dialogs.info(stage, "Password changed", "Your password was changed.");
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
