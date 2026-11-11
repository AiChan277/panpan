package app.panyaya.ui;

import app.panyaya.AppContext;
import app.panyaya.io.ImportError;
import app.panyaya.io.ImportPreview;
import app.panyaya.model.BakeryData;
import app.panyaya.model.BakerySettings;
import app.panyaya.persistence.JsonCodec;
import app.panyaya.report.DateRange;
import app.panyaya.ui.common.BaseController;
import app.panyaya.ui.common.Dialogs;
import app.panyaya.ui.common.UiAction;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

/** Settings: profile, thresholds, CSV import/export, integrity check and About. */
public class SettingsController extends BaseController {

    /** Import step that can throw (used to pass "commit" lambdas). */
    private interface Committer<T> {
        int commit(ImportPreview<T> preview) throws Exception;
    }

    /** Export step that writes a file. */
    private interface Exporter {
        void export(Path file) throws Exception;
    }

    @FXML
    private TextField nameField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextArea footerField;
    @FXML
    private TextField expiryDaysField;
    @FXML
    private TextField expiryHoursField;
    @FXML
    private TextField backupsField;
    @FXML
    private ComboBox<String> zoneBox;
    @FXML
    private Label aboutLabel;

    @Override
    protected void setUp() {
        zoneBox.getItems().setAll("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura", "Asia/Singapore", "Asia/Tokyo", "UTC");
    }

    @Override
    public void refresh() {
        BakerySettings settings = ctx.settings().getSettings();
        nameField.setText(settings.getBakeryName());
        addressField.setText(settings.getAddress());
        phoneField.setText(settings.getPhone());
        footerField.setText(settings.getReceiptFooter());
        expiryDaysField.setText(String.valueOf(settings.getNearExpiryDays()));
        expiryHoursField.setText(String.valueOf(settings.getNearExpiryHours()));
        backupsField.setText(String.valueOf(settings.getAutoBackupKeep()));
        zoneBox.setValue(settings.getTimeZone());
        updateAbout();
    }

    private void updateAbout() {
        BakeryData data = ctx.getStore().getData();
        Path file = ctx.getPaths().getDataFile();
        long size = 0;
        try {
            size = Files.size(file);
        } catch (IOException ignored) {
            // shown as 0
        }
        aboutLabel.setText(AppContext.APP_NAME + " " + AppContext.APP_VERSION + "  (パン屋ヤ)\n"
                + "Java " + System.getProperty("java.version") + " · JavaFX " + System.getProperty("javafx.runtime.version")
                + " · " + JsonCodec.libraryVersion() + "\n"
                + "Data format version " + data.getSchemaVersion() + " · save #" + data.getSaveCount() + " · "
                + Formatters.dateTime(data.getSavedAt(), data.getSettings().zone()) + "\n"
                + "Last operation: " + data.getLastOperation() + "\n"
                + "Data file: " + file + " (" + (size / 1024) + " KB)");
    }

    @FXML
    private void onSaveProfile() {
        if (attempt(() -> ctx.settings().updateProfile(nameField.getText(), addressField.getText(), phoneField.getText(),
                footerField.getText()))) {
            shell.setStatus("Bakery profile saved.");
        }
    }

    @FXML
    private void onSaveThresholds() {
        if (attempt(() -> ctx.settings().updateThresholds(
                NumberParser.parseWholeNumber(expiryDaysField.getText(), "Near-expiry days"),
                NumberParser.parseWholeNumber(expiryHoursField.getText(), "Near-expiry hours"),
                NumberParser.parseWholeNumber(backupsField.getText(), "Backups to keep"),
                zoneBox.getEditor().getText().trim()))) {
            shell.setStatus("Thresholds saved.");
        }
    }

    // ---- CSV import (generic: works for products, ingredients and suppliers) ----

    @FXML
    private void onImportProducts() {
        importCsv("products", file -> ctx.csv().previewProducts(file), preview -> ctx.csv().importProducts(preview));
    }

    @FXML
    private void onImportIngredients() {
        importCsv("ingredients", file -> ctx.csv().previewIngredients(file), preview -> ctx.csv().importIngredients(preview));
    }

    @FXML
    private void onImportSuppliers() {
        importCsv("suppliers", file -> ctx.csv().previewSuppliers(file), preview -> ctx.csv().importSuppliers(preview));
    }

    /** Previewer: reads the file without saving anything. */
    private interface Previewer<T> {
        ImportPreview<T> preview(Path file) throws Exception;
    }

    private <T> void importCsv(String what, Previewer<T> previewer, Committer<T> committer) {
        Path file = Dialogs.chooseOpenFile(window(), "Import " + what, "CSV file", ".csv", ctx.getPaths().getExportDir());
        if (file == null) {
            return;
        }
        Callable<ImportPreview<T>> dryRun = () -> previewer.preview(file);
        shell.tasks().run(window(), dryRun, preview -> {
            if (!preview.canCommit()) {
                StringBuilder text = new StringBuilder("Dry run of " + file.getFileName() + ": " + preview.getTotalRows()
                        + " row(s), " + preview.getErrors().size() + " error(s). NOTHING was imported.\n"
                        + "Fix these lines in the file and try again:\n\n");
                for (ImportError error : preview.getErrors()) {
                    text.append(error).append('\n');
                }
                Dialogs.showText(window(), "Import check failed", text.toString(), null, null);
                return;
            }
            if (Dialogs.confirm(window(), "Import " + what, "Dry run OK: " + preview.getRows().size() + " " + what
                    + " are valid. Import all of them now (one save)?")) {
                attempt(() -> {
                    int count = committer.commit(preview);
                    Dialogs.success(window(), "Import", count + " " + what + " imported.");
                });
            }
        });
    }

    // ---- CSV export ----

    private DateRange thisMonth() {
        return DateRange.thisMonth(LocalDate.ofInstant(ctx.getClock().instant(), ctx.settings().getSettings().zone()));
    }

    private void export(String name, Exporter exporter) {
        Path file = Dialogs.chooseSaveFile(window(), "Export " + name, name + ".csv", "CSV file", ".csv",
                ctx.getPaths().getExportDir());
        if (file == null) {
            return;
        }
        shell.tasks().run(window(), () -> {
            exporter.export(file); // file writing on a background thread
            return file;
        }, saved -> {
            shell.setStatus("Exported " + saved);
            Dialogs.success(window(), "Export", "Saved " + saved);
        });
    }

    @FXML
    private void onExportProducts() {
        export("products", file -> ctx.csv().exportProducts(file));
    }

    @FXML
    private void onExportIngredients() {
        export("ingredients", file -> ctx.csv().exportIngredients(file));
    }

    @FXML
    private void onExportSuppliers() {
        export("suppliers", file -> ctx.csv().exportSuppliers(file));
    }

    @FXML
    private void onExportSales() {
        DateRange range = thisMonth();
        export("sales-" + Formatters.ISO_DATE.format(range.getFrom()), file -> ctx.csv().exportSales(file, range));
    }

    @FXML
    private void onExportMovements() {
        DateRange range = thisMonth();
        export("stock-ledger-" + Formatters.ISO_DATE.format(range.getFrom()), file -> ctx.csv().exportMovements(file, range));
    }

    @FXML
    private void onExportWaste() {
        DateRange range = thisMonth();
        export("waste-" + Formatters.ISO_DATE.format(range.getFrom()), file -> ctx.csv().exportWaste(file, range));
    }

    // ---- integrity and folders ----

    @FXML
    private void onIntegrityCheck() {
        shell.tasks().run(window(), () -> {
            BakeryData copy = ctx.getStore().readCommittedCopy();
            return ctx.getStore().getValidator().validate(copy);
        }, (List<String> problems) -> {
            if (problems.isEmpty()) {
                Dialogs.success(window(), "Integrity check", "All checks passed. The data file is consistent.");
            } else {
                Dialogs.showText(window(), "Integrity check", problems.size() + " problem(s) found:\n\n"
                        + String.join("\n", problems), null, null);
            }
        });
    }

    @FXML
    private void onOpenData() {
        openFolder(ctx.getPaths().getDataDir());
    }

    @FXML
    private void onOpenLogs() {
        openFolder(ctx.getPaths().getLogDir());
    }

    private void openFolder(Path folder) {
        UiAction open = () -> new ProcessBuilder("explorer.exe", folder.toString()).start();
        try {
            open.run();
        } catch (Exception e) {
            Dialogs.info(window(), "Folder", folder.toString());
        }
    }
}
