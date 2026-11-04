package app.panyaya.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Locations of all files. Data lives in {@code %LOCALAPPDATA%\PanyaYa}, outside the installation
 * folder, so installing an update or uninstalling never deletes the bakery's records.
 *
 * <p>Override with {@code -Dpanyaya.home=C:\some\folder} or the environment variable
 * {@code PANYAYA_HOME} (used by tests, demos and developers who want a separate data set).</p>
 */
public class AppPaths {

    public static final String HOME_PROPERTY = "panyaya.home";
    public static final String DATA_FILE_NAME = "pan-ya-ya.json";

    private final Path home;

    public AppPaths(Path home) {
        this.home = home.toAbsolutePath();
    }

    public static AppPaths defaultPaths() {
        String override = System.getProperty(HOME_PROPERTY);
        if (override != null && !override.isBlank()) {
            return new AppPaths(Paths.get(override));
        }
        String envOverride = System.getenv("PANYAYA_HOME"); // e.g. a demo or USB data folder
        if (envOverride != null && !envOverride.isBlank()) {
            return new AppPaths(Paths.get(envOverride));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return new AppPaths(Paths.get(localAppData, "PanyaYa"));
        }
        return new AppPaths(Paths.get(System.getProperty("user.home"), ".panyaya"));
    }

    /** Creates the folder structure if it does not exist yet. */
    public void ensureDirectories() throws IOException {
        Files.createDirectories(getDataDir());
        Files.createDirectories(getAutoBackupDir());
        Files.createDirectories(getExportDir());
        Files.createDirectories(getQuarantineDir());
        Files.createDirectories(getLogDir());
    }

    public Path getHome() {
        return home;
    }

    public Path getDataDir() {
        return home.resolve("data");
    }

    public Path getDataFile() {
        return getDataDir().resolve(DATA_FILE_NAME);
    }

    public Path getBackupDir() {
        return home.resolve("backups");
    }

    public Path getAutoBackupDir() {
        return getBackupDir().resolve("auto");
    }

    public Path getExportDir() {
        return home.resolve("exports");
    }

    public Path getQuarantineDir() {
        return home.resolve("quarantine");
    }

    public Path getLogDir() {
        return home.resolve("logs");
    }

    public Path getLockFile() {
        return home.resolve("panyaya.lock");
    }
}
