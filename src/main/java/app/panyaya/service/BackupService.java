package app.panyaya.service;

import app.panyaya.exception.AuthorizationException;
import app.panyaya.exception.DataCorruptedException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.io.Checksums;
import app.panyaya.model.BakeryData;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.AppPaths;
import app.panyaya.persistence.DataStore;
import app.panyaya.util.Formatters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backups and restores.
 *
 * <p>A backup is a ZIP file ({@code .panyabak}) with two entries: {@code pan-ya-ya.json} (the data)
 * and {@code manifest.json} (checksum, counts, versions). Course topic <b>Java IO</b>:
 * {@link ZipOutputStream}, {@link ZipFile}, {@link DirectoryStream}, atomic rename.</p>
 *
 * <p>Consistency without locks: the backup writes the last saved JSON bytes held by the
 * {@link DataStore} (an immutable snapshot), so it can run on a background thread while sales
 * continue, and it never opens the live data file.</p>
 */
public class BackupService extends BaseService {

    public static final String EXTENSION = ".panyabak";
    static final String DATA_ENTRY = "pan-ya-ya.json";
    static final String MANIFEST_ENTRY = "manifest.json";
    private static final long MAX_ENTRY_BYTES = 200L * 1024 * 1024; // protection against "zip bombs"

    private final AppPaths paths;
    private final String appVersion;

    public BackupService(DataStore store, Session session, Clock clock, AppPaths paths, String appVersion) {
        super(store, session, clock);
        this.paths = paths;
        this.appVersion = appVersion;
    }

    /** Manual backup to a file chosen by the Owner. */
    public Path createBackup(Path target, String reason, ProgressListener progress)
            throws AuthorizationException, PersistenceException {
        session.require(Permission.MANAGE_BACKUP);
        return writeBackup(target, reason, progress);
    }

    /**
     * Creates today's automatic backup if it does not exist yet, then deletes the oldest automatic
     * backups beyond the configured number. Called in the background after sign-in.
     */
    public Path createDailyBackupIfNeeded() throws PersistenceException {
        LocalDate today = today();
        String prefix = "daily-" + Formatters.ISO_DATE.format(today).replace("-", "");
        try {
            Files.createDirectories(paths.getAutoBackupDir());
            for (Path existing : listBackups(paths.getAutoBackupDir())) {
                if (existing.getFileName().toString().startsWith(prefix)) {
                    return null; // already done today
                }
            }
        } catch (IOException e) {
            throw new PersistenceException("Cannot read the backup folder: " + e.getMessage(), e);
        }
        String stamp = Formatters.FILE_STAMP.format(LocalDateTime.ofInstant(now(), zone()));
        Path target = paths.getAutoBackupDir().resolve("daily-" + stamp + EXTENSION);
        Path created = writeBackup(target, "DAILY", ProgressListener.NONE);
        applyRetention(data().getSettings().getAutoBackupKeep());
        return created;
    }

    private Path writeBackup(Path target, String reason, ProgressListener progress) throws PersistenceException {
        byte[] snapshot = store.getCommittedBytes(); // immutable: consistent without locking
        progress.update(0.1, "Reading data...");
        BakeryData decoded = store.getCodec().decode(snapshot);

        BackupManifest manifest = new BackupManifest();
        manifest.appVersion = appVersion;
        manifest.schemaVersion = decoded.getSchemaVersion();
        manifest.saveCount = decoded.getSaveCount();
        manifest.createdAt = now();
        manifest.reason = reason;
        manifest.bakeryName = decoded.getSettings().getBakeryName();
        manifest.dataEntry = DATA_ENTRY;
        manifest.dataBytes = snapshot.length;
        manifest.dataSha256 = Checksums.sha256(snapshot);
        manifest.counts.put("products", decoded.getProducts().size());
        manifest.counts.put("ingredients", decoded.getIngredients().size());
        manifest.counts.put("recipes", decoded.getRecipes().size());
        manifest.counts.put("sales", decoded.getSales().size());
        manifest.counts.put("productionOrders", decoded.getProductionOrders().size());
        manifest.counts.put("wasteRecords", decoded.getWasteRecords().size());
        manifest.counts.put("movements", decoded.getMovements().size());
        byte[] manifestBytes = store.getCodec().encodeObject(manifest);

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            progress.update(0.3, "Writing ZIP file...");
            try (OutputStream out = Files.newOutputStream(temp);
                 ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry(DATA_ENTRY));
                zip.write(snapshot);
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
                zip.write(manifestBytes);
                zip.closeEntry();
            }
            progress.update(0.6, "Verifying backup...");
            inspectFile(temp); // re-open and verify before it counts as a backup
            progress.update(0.9, "Finishing...");
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            progress.update(1.0, "Backup complete");
            return target;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new PersistenceException("Backup failed: " + e.getMessage(), e);
        } catch (DataCorruptedException e) {
            deleteQuietly(temp);
            throw new PersistenceException("Backup verification failed: " + e.getMessage(), e);
        }
    }

    /** Opens a backup and verifies it completely, without changing anything. */
    public BackupInfo inspect(Path backupFile) throws PersistenceException {
        try {
            return inspectFile(backupFile);
        } catch (IOException e) {
            throw new DataCorruptedException("Cannot open backup " + backupFile.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private BackupInfo inspectFile(Path file) throws IOException, DataCorruptedException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST_ENTRY);
            ZipEntry dataEntry = zip.getEntry(DATA_ENTRY);
            if (manifestEntry == null || dataEntry == null) {
                throw new DataCorruptedException("This is not a Pan-ya Ya backup (missing manifest or data).");
            }
            BackupManifest manifest = store.getCodec().decodeObject(readEntry(zip, manifestEntry), BackupManifest.class);
            if (!BackupManifest.FORMAT.equals(manifest.format)) {
                throw new DataCorruptedException("Unknown backup format \"" + manifest.format + "\".");
            }
            if (manifest.schemaVersion > BakeryData.CURRENT_SCHEMA_VERSION) {
                throw new DataCorruptedException("The backup was made by a newer Pan-ya Ya version (data format "
                        + manifest.schemaVersion + ").");
            }
            byte[] data = readEntry(zip, dataEntry);
            if (data.length != manifest.dataBytes || !Checksums.sha256(data).equals(manifest.dataSha256)) {
                throw new DataCorruptedException("The backup is damaged: the checksum does not match.");
            }
            // full decode + integrity validation, exactly like loading the data file
            BakeryData decoded = store.getCodec().decode(data);
            List<String> problems = store.getValidator().validate(decoded);
            if (!problems.isEmpty()) {
                throw new DataCorruptedException("The backup data failed the integrity check", problems);
            }
            return new BackupInfo(file, manifest, data);
        }
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_ENTRY_BYTES) {
                    throw new IOException("Backup entry is unreasonably large");
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    /**
     * Restores a backup: verify -> automatic pre-restore backup of the current data -> replace.
     * Nothing changes if any check fails.
     */
    public void restore(Path backupFile, ProgressListener progress) throws AuthorizationException, PersistenceException {
        if (store.isLoaded()) {
            session.require(Permission.MANAGE_BACKUP);
        }
        progress.update(0.1, "Verifying backup...");
        BackupInfo info = inspect(backupFile);
        if (store.isLoaded()) {
            progress.update(0.4, "Saving the current data first (pre-restore backup)...");
            String stamp = Formatters.FILE_STAMP.format(LocalDateTime.ofInstant(now(), zone()));
            writeBackup(paths.getAutoBackupDir().resolve("pre-restore-" + stamp + EXTENSION), "PRE_RESTORE",
                    ProgressListener.NONE);
        }
        progress.update(0.7, "Restoring...");
        store.replaceAll(info.getDataBytes(), "RESTORE_BACKUP " + backupFile.getFileName());
        progress.update(1.0, "Restore complete");
    }

    /** Backup files in a folder, newest first. */
    public List<Path> listBackups(Path folder) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*" + EXTENSION)) {
            for (Path path : stream) {
                result.add(path);
            }
        }
        result.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        return result;
    }

    /** Keeps the newest {@code keep} daily backups; older daily backups are deleted. */
    void applyRetention(int keep) {
        try {
            List<Path> daily = new ArrayList<>();
            for (Path path : listBackups(paths.getAutoBackupDir())) {
                if (path.getFileName().toString().startsWith("daily-")) {
                    daily.add(path);
                }
            }
            for (int i = keep; i < daily.size(); i++) {
                Files.deleteIfExists(daily.get(i));
            }
        } catch (IOException e) {
            // retention is housekeeping; a failure must not break the backup that just succeeded
        }
    }

    public Path getAutoBackupDir() {
        return paths.getAutoBackupDir();
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
