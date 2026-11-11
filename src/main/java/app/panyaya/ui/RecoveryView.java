package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.persistence.StartupInspector;
import app.panyaya.service.BackupService;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.TaskRunner;
import app.panyaya.ui.common.Icon;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;

/**
 * Recovery screen, shown instead of the sign-in when the data file is missing or damaged. It never
 * starts with empty data on its own: the Owner chooses the previous version, a backup, or (with a
 * typed confirmation) a brand-new bakery.
 */
public class RecoveryView {

    private final StackPane root = new StackPane();

    public RecoveryView(AppContext ctx, TaskRunner tasks, Window owner, StartupInspector.Result result,
                        Runnable onRecovered, Runnable onStartNew) {
        Label title = new Label("Data recovery");
        title.getStyleClass().add("page-title");
        Label problem = new Label(result.getMessage(), new Icon("alert", 18));
        problem.setWrapText(true);
        problem.setMaxWidth(Double.MAX_VALUE);
        problem.getStyleClass().add("banner-warning");
        Label explanation = new Label("Pan-ya Ya keeps the previous version of the data file and automatic daily "
                + "backups. Choose how to continue:");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");

        Button previous = new Button("Use the previous version of the data file (loses at most the last change)");
        previous.getStyleClass().addAll("primary", "recovery-option");
        previous.setGraphic(new Icon("restore", 16));
        previous.setWrapText(true);
        previous.setMaxWidth(Double.MAX_VALUE);
        previous.setDisable(!ctx.getStartupInspector().previousGenerationExists());
        previous.setOnAction(e -> tasks.run(owner, () -> {
            ctx.getStartupInspector().recoverFromPreviousGeneration();
            return Boolean.TRUE;
        }, ok -> {
            Dialogs.success(owner, "Recovered", "The previous version was restored. Please sign in.");
            onRecovered.run();
        }));

        Button backup = new Button("Restore from a backup file (.panyabak)...");
        backup.setMaxWidth(Double.MAX_VALUE);
        backup.getStyleClass().add("recovery-option");
        backup.setGraphic(new Icon("database", 16));
        backup.setOnAction(e -> {
            Path file = Dialogs.chooseOpenFile(owner, "Choose a backup", "Pan-ya Ya backup",
                    BackupService.EXTENSION, ctx.backups().getAutoBackupDir());
            if (file == null) {
                return;
            }
            tasks.runWithProgress(owner, "Restoring backup", progress -> {
                ctx.backups().restore(file, progress);
                return Boolean.TRUE;
            }, ok -> {
                Dialogs.success(owner, "Restored", "The backup was restored. Please sign in.");
                onRecovered.run();
            });
        });

        Button startNew = new Button("Start a new, empty bakery");
        startNew.getStyleClass().addAll("danger", "recovery-option");
        startNew.setGraphic(new Icon("plus", 16));
        startNew.setMaxWidth(Double.MAX_VALUE);
        startNew.setOnAction(e -> {
            if (Dialogs.confirmTyped(owner, "Start a new bakery",
                    "This starts with NO products, recipes or sales. Damaged files stay in the quarantine folder "
                            + "and backups are not deleted.", "NEW")) {
                onStartNew.run();
            }
        });

        Label folder = new Label("Data folder: " + ctx.getPaths().getHome());
        folder.getStyleClass().add("hint");

        VBox card = new VBox(12, title, problem, explanation, previous, backup, startNew, folder);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(620);
        card.setMaxHeight(Double.NEGATIVE_INFINITY);
        root.getChildren().add(card);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().addAll("auth-root", "centered-page");
    }

    public Parent getRoot() {
        return root;
    }
}
