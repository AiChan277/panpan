package app.panyaya.persistence;

import app.panyaya.exception.DataCorruptedException;
import app.panyaya.exception.PersistenceException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decides what happens at start-up. The rule that matters most: <b>the application never opens with
 * empty data when data or backups exist.</b>
 *
 * <ul>
 *   <li>leftover {@code .tmp} file: an interrupted save, deleted (it never became the data file);</li>
 *   <li>no data file and no backups: first run, show the setup wizard;</li>
 *   <li>no data file but a previous generation or backups exist: recovery screen;</li>
 *   <li>data file damaged or failing the integrity check: move it to {@code quarantine\} and show
 *       the recovery screen (previous generation or backup).</li>
 * </ul>
 */
public class StartupInspector {

    /** What the application should show first. */
    public enum State {
        READY,
        FIRST_RUN,
        RECOVERY_NEEDED
    }

    /** Result of the inspection. */
    public static class Result {
        private final State state;
        private final String message;
        private final Path quarantinedFile;

        Result(State state, String message, Path quarantinedFile) {
            this.state = state;
            this.message = message;
            this.quarantinedFile = quarantinedFile;
        }

        public State getState() {
            return state;
        }

        public String getMessage() {
            return message;
        }

        public Path getQuarantinedFile() {
            return quarantinedFile;
        }
    }

    private final AppPaths paths;
    private final FileDataStorage storage;
    private final DataStore store;

    public StartupInspector(AppPaths paths, FileDataStorage storage, DataStore store) {
        this.paths = paths;
        this.storage = storage;
        this.store = store;
    }

    public Result inspect() throws PersistenceException {
        try {
            paths.ensureDirectories();
            storage.deleteLeftoverTemp();
        } catch (IOException e) {
            throw new PersistenceException("Cannot prepare the data folder " + paths.getHome() + ": " + e.getMessage(), e);
        }

        if (!storage.exists()) {
            if (storage.previousExists() || hasBackups()) {
                return new Result(State.RECOVERY_NEEDED,
                        "The data file is missing, but a previous version or backups exist. "
                                + "Choose what to restore (nothing will start empty by accident).", null);
            }
            return new Result(State.FIRST_RUN, "Welcome! Let's set up your bakery.", null);
        }

        try {
            store.load();
            return new Result(State.READY, "Data loaded.", null);
        } catch (DataCorruptedException e) {
            Path quarantined;
            try {
                quarantined = storage.quarantinePrimary(paths.getQuarantineDir());
            } catch (IOException moveError) {
                throw new PersistenceException("The data file is damaged and could not be moved to quarantine: "
                        + moveError.getMessage(), moveError);
            }
            return new Result(State.RECOVERY_NEEDED, "The data file could not be used: " + e.getMessage()
                    + "\nIt was moved to " + quarantined + " (not deleted).", quarantined);
        }
    }

    /** Recovery option 1: use pan-ya-ya.prev.json (loses at most the last save). */
    public void recoverFromPreviousGeneration() throws PersistenceException {
        byte[] previous;
        try {
            previous = storage.readPrevious();
        } catch (IOException e) {
            throw new PersistenceException("Cannot read the previous data file: " + e.getMessage(), e);
        }
        store.replaceAll(previous, "RECOVER_PREVIOUS_GENERATION");
    }

    public boolean previousGenerationExists() {
        return storage.previousExists();
    }

    private boolean hasBackups() {
        Path folder = paths.getAutoBackupDir();
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.panyabak")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }
}
