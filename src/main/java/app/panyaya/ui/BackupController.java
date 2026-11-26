package app.panyaya.ui;

import app.panyaya.service.BackupInfo;
import app.panyaya.service.BackupManifest;
import app.panyaya.service.BackupService;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiUtil;
import app.panyaya.util.Formatters;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

/** Backup and restore. All file work runs on background threads with progress feedback. */
public class BackupController extends BaseController {

    @FXML
    private TableView<Path> table;
    @FXML
    private Label locationLabel;

    @Override
    protected void setUp() {
        table.getColumns().setAll(Arrays.asList(
                UiUtil.<Path>column("File", 360, p -> p.getFileName().toString()),
                UiUtil.<Path>column("Type", 120, p -> p.getFileName().toString().startsWith("pre-restore") ? "Before restore" : "Daily"),
                UiUtil.<Path>numberColumn("Size", 100, p -> sizeOf(p))));
        UiUtil.placeholder(table, "No automatic backups yet. One is made every day after the first sign-in.");
    }

    @Override
    public void refresh() {
        try {
            UiUtil.setItems(table, ctx.backups().listBackups(ctx.backups().getAutoBackupDir()));
        } catch (IOException e) {
            Dialogs.error(window(), e);
        }
        locationLabel.setText("Live data: " + ctx.getPaths().getDataFile() + "   ·   Automatic backups: "
                + ctx.backups().getAutoBackupDir());
    }

    private static String sizeOf(Path path) {
        try {
            return (Files.size(path) / 1024) + " KB";
        } catch (IOException e) {
            return "?";
        }
    }

    @FXML
    private void onBackup() {
        Path folder = Paths.get(System.getProperty("user.home"), "Documents", "Pan-ya Ya Backup");
        String name = "panyaya-" + Formatters.FILE_STAMP.format(LocalDateTime.now()) + BackupService.EXTENSION;
        Path target = Dialogs.chooseSaveFile(window(), "Create backup", name, "Pan-ya Ya backup", BackupService.EXTENSION,
                Files.isDirectory(folder) ? folder : null);
        if (target == null) {
            return;
        }
        shell.tasks().runWithProgress(window(), "Creating backup",
                progress -> ctx.backups().createBackup(target, "MANUAL", progress),
                file -> {
                    Dialogs.success(window(), "Backup created", "Backup verified and saved:\n" + file);
                    refresh();
                });
    }

    @FXML
    private void onRestoreFile() {
        Path file = Dialogs.chooseOpenFile(window(), "Choose a backup", "Pan-ya Ya backup", BackupService.EXTENSION,
                ctx.backups().getAutoBackupDir());
        if (file != null) {
            restore(file);
        }
    }

    @FXML
    private void onRestoreSelected() {
        Path file = table.getSelectionModel().getSelectedItem();
        if (file == null) {
            Dialogs.info(window(), "Restore", "Select a backup first.");
        } else {
            restore(file);
        }
    }

    @FXML
    private void onVerify() {
        Path file = table.getSelectionModel().getSelectedItem();
        if (file == null) {
            return;
        }
        shell.tasks().run(window(), () -> ctx.backups().inspect(file),
                info -> Dialogs.showText(window(), "Backup verified", "Checksum and integrity OK.\n\n" + describe(info),
                        null, null));
    }

    /** Verify -> show summary -> typed confirmation -> restore with progress -> sign in again. */
    private void restore(Path file) {
        shell.tasks().run(window(), () -> ctx.backups().inspect(file), info -> {
            if (!Dialogs.confirmTyped(window(), "Restore backup", "Replace ALL current data with this backup?\n\n"
                    + describe(info) + "\nYour current data will first be saved as a pre-restore backup.", "RESTORE")) {
                return;
            }
            shell.tasks().runWithProgress(window(), "Restoring backup", progress -> {
                ctx.backups().restore(file, progress);
                return Boolean.TRUE;
            }, ok -> {
                Dialogs.success(window(), "Restored", "The backup was restored. Please sign in again.");
                shell.signOutWithoutAsking();
            });
        });
    }

    private String describe(BackupInfo info) {
        BackupManifest manifest = info.getManifest();
        StringBuilder text = new StringBuilder();
        text.append("File:       ").append(info.getFile().getFileName()).append('\n');
        text.append("Bakery:     ").append(manifest.getBakeryName()).append('\n');
        text.append("Created:    ").append(Formatters.dateTime(manifest.getCreatedAt(), ctx.settings().getSettings().zone()))
                .append(" (").append(manifest.getReason()).append(")\n");
        text.append("App:        ").append(manifest.getAppVersion()).append(", data format ").append(manifest.getSchemaVersion())
                .append(", save #").append(manifest.getSaveCount()).append('\n');
        text.append("SHA-256:    ").append(manifest.getDataSha256(), 0, 16).append("...\n");
        for (Map.Entry<String, Integer> count : manifest.getCounts().entrySet()) {
            text.append("  ").append(count.getKey()).append(": ").append(count.getValue()).append('\n');
        }
        return text.toString();
    }

    @FXML
    private void onOpenFolder() {
        try {
            new ProcessBuilder("explorer.exe", ctx.backups().getAutoBackupDir().toString()).start();
        } catch (IOException e) {
            Dialogs.info(window(), "Backup folder", ctx.backups().getAutoBackupDir().toString());
        }
    }
}
