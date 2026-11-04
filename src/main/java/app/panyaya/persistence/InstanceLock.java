package app.panyaya.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Makes sure only one Pan-ya Ya window uses a data folder at a time. Two running copies would
 * overwrite each other's saves. The operating system releases the lock automatically if the
 * application crashes.
 */
public final class InstanceLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /** @return the lock, or {@code null} when another Pan-ya Ya instance already holds it */
    public static InstanceLock tryAcquire(Path lockFile) throws IOException {
        Files.createDirectories(lockFile.getParent());
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return null;
            }
            return new InstanceLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            channel.close(); // already locked by this same JVM
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        if (lock.isValid()) {
            lock.release();
        }
        channel.close();
    }
}
