package app.panyaya.persistence;

import app.panyaya.util.Formatters;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Stores the data in one JSON file and replaces it safely.
 *
 * <p>Course topic <b>Java IO</b>. A plain "overwrite the file" is dangerous: Windows first empties the
 * file, so a crash or power cut in the middle leaves half a file. This class instead:</p>
 * <ol>
 *   <li>writes the new content to {@code pan-ya-ya.json.tmp} with a {@link FileChannel};</li>
 *   <li>calls {@link FileChannel#force(boolean)} so the bytes are really on the disk;</li>
 *   <li>checks the temp file has the expected size;</li>
 *   <li>copies the current file to {@code pan-ya-ya.prev.json} (previous generation);</li>
 *   <li>replaces the primary file with {@link Files#move} using {@code ATOMIC_MOVE}, so other
 *       programs see either the old file or the new file, never a mixture;</li>
 *   <li>on any failure deletes the temp file and rethrows - the old file is untouched.</li>
 * </ol>
 * <p>Limit (documented, not hidden): this is not a database. After a sudden power loss right after a
 * save, Windows may keep the previous generation instead of the newest one.</p>
 */
public class FileDataStorage implements DataStorage {

    private static final int MOVE_ATTEMPTS = 5;

    private final Path primary;
    private final Path previous;
    private final Path temp;

    public FileDataStorage(Path primaryFile) {
        this.primary = primaryFile;
        String fileName = primaryFile.getFileName().toString();
        String base = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
        this.previous = primaryFile.resolveSibling(base + ".prev.json");
        this.temp = primaryFile.resolveSibling(fileName + ".tmp");
    }

    @Override
    public boolean exists() {
        return Files.exists(primary);
    }

    @Override
    public byte[] read() throws IOException {
        return Files.readAllBytes(primary);
    }

    public boolean previousExists() {
        return Files.exists(previous);
    }

    public byte[] readPrevious() throws IOException {
        return Files.readAllBytes(previous);
    }

    @Override
    public void write(byte[] content) throws IOException {
        Files.createDirectories(primary.getParent());
        try {
            writeTempFile(content);
            if (Files.size(temp) != content.length) {
                throw new IOException("Temporary file has the wrong size after writing");
            }
            if (Files.exists(primary)) {
                Files.copy(primary, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            moveWithRetry();
        } catch (IOException | RuntimeException e) {
            // the temp file never became the data file: remove it, keep the old data file
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private void writeTempFile(byte[] content) throws IOException {
        // try-with-resources closes the channel even if writing fails
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true); // flush file content to the storage device
        }
    }

    /**
     * Antivirus or indexing programs sometimes hold the file open for a moment on Windows, which
     * makes the move fail. We retry a few times with a growing pause before giving up.
     */
    private void moveWithRetry() throws IOException {
        long waitMillis = 50;
        for (int attempt = 1; ; attempt++) {
            try {
                try {
                    Files.move(temp, primary, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    // some file systems (e.g. network drives) cannot do atomic moves
                    Files.move(temp, primary, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= MOVE_ATTEMPTS) {
                    throw e;
                }
            } catch (FileSystemException e) {
                if (attempt >= MOVE_ATTEMPTS) {
                    throw e;
                }
            }
            sleepQuietly(waitMillis);
            waitMillis = waitMillis * 2;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A leftover temp file means a save was interrupted; it never became the data file. */
    public boolean deleteLeftoverTemp() throws IOException {
        return Files.deleteIfExists(temp);
    }

    /** Moves the (damaged) primary file into the quarantine folder. Never deletes it. */
    public Path quarantinePrimary(Path quarantineDir) throws IOException {
        Files.createDirectories(quarantineDir);
        String stamp = Formatters.FILE_STAMP.format(LocalDateTime.now());
        Path target = quarantineDir.resolve("pan-ya-ya-" + stamp + ".json");
        Files.move(primary, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public Path getPrimary() {
        return primary;
    }

    public Path getPrevious() {
        return previous;
    }

    public Path getTemp() {
        return temp;
    }

    @Override
    public String describe() {
        return primary.toString();
    }
}
