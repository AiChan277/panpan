package app.panyaya.persistence;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.DataCorruptedException;
import app.panyaya.exception.DataIntegrityException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.model.BakeryData;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the bakery data in memory and saves every change to disk as one all-or-nothing operation.
 *
 * <p>How {@link #execute} works (this is the heart of the persistence design):</p>
 * <ol>
 *   <li>the operation (a lambda from a service) changes the in-memory {@link BakeryData};</li>
 *   <li>{@link DataValidator} checks the whole data set;</li>
 *   <li>the data is encoded to JSON and written through {@link DataStorage#write} (atomic);</li>
 *   <li>only now is the new JSON remembered as the "last committed" state.</li>
 * </ol>
 * <p>If <b>any</b> step fails - a business rule, the validator, or the disk - the in-memory data is
 * rebuilt from the last committed JSON bytes. Memory and disk therefore always agree, and a failed
 * save is never shown as a success.</p>
 *
 * <p>Course topic <b>Multithreading</b>: {@code execute} is {@code synchronized}, so two threads can
 * never change the data at the same time. Background threads (backups, reports) do not touch the
 * live objects at all: they read {@link #getCommittedBytes()}, an immutable snapshot that is
 * replaced (never modified) after each save and published through a {@code volatile} field.</p>
 */
public class DataStore {

    private final DataStorage storage;
    private final JsonCodec codec;
    private final DataValidator validator;
    private final Clock clock;
    private final String appVersion;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private BakeryData data;
    private volatile byte[] committedBytes;

    public DataStore(DataStorage storage, JsonCodec codec, DataValidator validator, Clock clock, String appVersion) {
        this.storage = storage;
        this.codec = codec;
        this.validator = validator;
        this.clock = clock;
        this.appVersion = appVersion;
    }

    /** Loads and fully validates the saved data. Throws if the file is missing, unreadable or invalid. */
    public synchronized BakeryData load() throws PersistenceException {
        byte[] content;
        try {
            content = storage.read();
        } catch (IOException e) {
            throw new PersistenceException("Could not read " + storage.describe() + ": " + e.getMessage(), e);
        }
        BakeryData loaded = decodeAndValidate(content);
        this.data = loaded;
        this.committedBytes = content;
        return loaded;
    }

    /** Creates a brand-new data file (first run). */
    public synchronized void initialize(BakeryData fresh) throws PersistenceException {
        List<String> problems = validator.validate(fresh);
        if (!problems.isEmpty()) {
            throw new DataIntegrityException(problems);
        }
        fresh.markSaved("CREATE_DATA_FILE", clock.instant(), appVersion);
        byte[] bytes = codec.encode(fresh);
        writeToStorage(bytes);
        this.data = fresh;
        this.committedBytes = bytes;
        notifyListeners();
    }

    /**
     * Runs one business operation as an all-or-nothing change.
     *
     * @param operationName short label stored in the file as "lastOperation"
     */
    public synchronized <T> T execute(String operationName, DataOperation<T> operation)
            throws BusinessException, PersistenceException {
        if (data == null || committedBytes == null) {
            throw new IllegalStateException("No data loaded");
        }
        try {
            T result = operation.apply(data);

            List<String> problems = validator.validate(data);
            if (!problems.isEmpty()) {
                throw new DataIntegrityException(problems);
            }
            data.markSaved(operationName, clock.instant(), appVersion);
            byte[] bytes = codec.encode(data);
            writeToStorage(bytes);

            committedBytes = bytes; // publish the new committed snapshot
            notifyListeners();
            return result;
        } catch (BusinessException | PersistenceException | RuntimeException e) {
            rollback();
            throw e;
        }
    }

    /**
     * Replaces all data with new content (restore from backup or previous generation). The new
     * content is fully validated before anything is written.
     */
    public synchronized void replaceAll(byte[] newContent, String operationName) throws PersistenceException {
        BakeryData restored = decodeAndValidate(newContent);
        restored.markSaved(operationName, clock.instant(), appVersion);
        byte[] bytes = codec.encode(restored);
        writeToStorage(bytes);
        this.data = restored;
        this.committedBytes = bytes;
        notifyListeners();
    }

    /** Throws away uncommitted in-memory changes by rebuilding the data from the last saved JSON. */
    private void rollback() {
        try {
            data = codec.decode(committedBytes);
        } catch (DataCorruptedException e) {
            // cannot happen: these exact bytes were decoded or produced successfully before
            throw new IllegalStateException("Rollback failed", e);
        }
    }

    private BakeryData decodeAndValidate(byte[] content) throws DataCorruptedException {
        BakeryData decoded = codec.decode(content);
        List<String> problems = validator.validate(decoded);
        if (!problems.isEmpty()) {
            throw new DataCorruptedException("The data failed the integrity check", problems);
        }
        return decoded;
    }

    private void writeToStorage(byte[] bytes) throws PersistenceException {
        try {
            storage.write(bytes);
        } catch (IOException e) {
            throw new PersistenceException("Saving failed - nothing was saved. (" + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + ")", e);
        }
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    /** Called after every successful save (used by the UI to refresh, e.g. the dashboard). */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    public synchronized boolean isLoaded() {
        return data != null;
    }

    /** Live data, for reading on the UI thread. Changes must go through {@link #execute}. */
    public synchronized BakeryData getData() {
        if (data == null) {
            throw new IllegalStateException("No data loaded");
        }
        return data;
    }

    /** The last saved JSON. Safe to use from any thread: the array is never modified. */
    public byte[] getCommittedBytes() {
        return committedBytes;
    }

    /** An independent copy of the committed data, for background work such as reports. */
    public BakeryData readCommittedCopy() throws DataCorruptedException {
        byte[] snapshot = committedBytes;
        if (snapshot == null) {
            throw new IllegalStateException("No data loaded");
        }
        return codec.decode(snapshot);
    }

    public JsonCodec getCodec() {
        return codec;
    }

    public DataValidator getValidator() {
        return validator;
    }

    public DataStorage getStorage() {
        return storage;
    }
}
