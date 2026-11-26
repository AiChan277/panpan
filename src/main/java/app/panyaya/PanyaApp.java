package app.panyaya;

import app.panyaya.persistence.AppPaths;
import app.panyaya.persistence.InstanceLock;
import app.panyaya.persistence.StartupInspector;
import app.panyaya.service.PasswordHasher;
import app.panyaya.ui.LoginController;
import app.panyaya.ui.MainController;
import app.panyaya.ui.RecoveryView;
import app.panyaya.ui.SetupController;
import app.panyaya.ui.common.TaskRunner;
import app.panyaya.util.AppLog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

/**
 * The JavaFX application: acquires the single-instance lock, loads the data in the background and
 * then shows the setup wizard, the recovery screen or the sign-in screen.
 */
public class PanyaApp extends Application {

    public static final String CSS = "/app/panyaya/ui/css/panyaya.css";

    private Stage stage;
    private Scene scene;
    private AppContext ctx;
    private TaskRunner tasks;
    private InstanceLock lock;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        AppPaths paths = AppPaths.defaultPaths();
        try {
            paths.ensureDirectories();
            AppLog.configure(paths.getLogDir());
            lock = InstanceLock.tryAcquire(paths.getLockFile());
        } catch (IOException e) {
            fatal("Pan-ya Ya cannot use its data folder " + paths.getHome() + ":\n" + e.getMessage());
            return;
        }
        if (lock == null) {
            fatal("Pan-ya Ya is already running. Only one window may use the bakery data at a time.");
            return;
        }
        AppLog.info("Starting Pan-ya Ya " + AppContext.APP_VERSION + ", data in " + paths.getHome());

        ctx = new AppContext(paths, Clock.systemUTC(), new PasswordHasher());
        tasks = new TaskRunner(ctx.getBackgroundExecutor());

        scene = new Scene(loadingView(), 1280, 800);
        scene.getStylesheets().add(getClass().getResource(CSS).toExternalForm());
        stage.setScene(scene);
        stage.setTitle(AppContext.APP_NAME + " - Bakery Operations");
        // the layout adapts below 1240 px (icon sidebar, compact toolbars), so smaller windows work
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        addIcon(stage);
        stage.show();

        // start-up inspection reads and validates the data file: background thread
        tasks.run(() -> ctx.getStartupInspector().inspect(), this::route, this::startupFailed);
    }

    private void route(StartupInspector.Result result) {
        switch (result.getState()) {
            case FIRST_RUN:
                showSetup();
                break;
            case RECOVERY_NEEDED:
                showRecovery(result);
                break;
            default:
                showLogin();
                break;
        }
    }

    private void startupFailed(Throwable error) {
        AppLog.error("Startup failed", error);
        fatal("Pan-ya Ya could not start: " + error.getMessage());
    }

    public void showSetup() {
        SetupController controller = setRoot("/app/panyaya/ui/SetupView.fxml");
        controller.init(ctx, tasks, this::showMain);
    }

    public void showLogin() {
        LoginController controller = setRoot("/app/panyaya/ui/LoginView.fxml");
        controller.init(ctx, tasks, this::showMain);
    }

    private void showRecovery(StartupInspector.Result result) {
        RecoveryView recovery = new RecoveryView(ctx, tasks, stage, result, this::showLogin, this::showSetup);
        scene.setRoot(recovery.getRoot());
    }

    private void showMain() {
        MainController controller = setRoot("/app/panyaya/ui/MainView.fxml");
        controller.start(ctx, tasks, stage, this::showLogin);
    }

    private <T> T setRoot(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            scene.setRoot(root);
            return loader.getController();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load " + fxml, e);
        }
    }

    private static Parent loadingView() {
        ImageView logo = new ImageView(new Image(PanyaApp.class.getResource("/app/panyaya/ui/img/icon-64.png").toExternalForm()));
        logo.setFitWidth(56);
        logo.setFitHeight(56);
        logo.setSmooth(true);
        Label brand = new Label("パン屋ヤ");
        brand.getStyleClass().add("brand-jp");
        Label name = new Label("PAN-YA YA  ·  BAKERY OPERATIONS");
        name.getStyleClass().add("brand-latin");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(28, 28);
        Label message = new Label("Loading bakery data...");
        message.getStyleClass().add("muted");
        VBox titles = new VBox(2, brand, name);
        titles.setAlignment(Pos.CENTER);
        VBox box = new VBox(18, logo, titles, progress, message);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("loading-view");
        return box;
    }

    private static void addIcon(Stage stage) {
        try (InputStream icon = PanyaApp.class.getResourceAsStream("/app/panyaya/ui/img/icon-256.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        } catch (IOException ignored) {
            // the window simply has the default icon
        }
    }

    private void fatal(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.setTitle(AppContext.APP_NAME);
        alert.showAndWait();
        Platform.exit();
    }

    @Override
    public void stop() {
        if (ctx != null) {
            ctx.shutdown();
        }
        if (lock != null) {
            try {
                lock.close();
            } catch (IOException ignored) {
                // released by the OS when the process ends
            }
        }
    }
}
