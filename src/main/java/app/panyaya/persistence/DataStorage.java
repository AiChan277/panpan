package app.panyaya.persistence;

import java.io.IOException;

/**
 * Where the encoded data bytes are kept.
 *
 * <p>Course topic <b>Abstraction</b>: the {@link DataStore} only knows this interface. The real
 * application uses {@link FileDataStorage} (a JSON file on disk); the tests also use an in-memory
 * storage and a deliberately failing storage to prove that a failed save loses nothing.</p>
 */
public interface DataStorage {

    /** True when saved data exists. */
    boolean exists();

    /** Reads the complete saved content. */
    byte[] read() throws IOException;

    /**
     * Replaces the saved content. Implementations must be all-or-nothing: after a failure the
     * previous content must still be readable.
     */
    void write(byte[] content) throws IOException;

    /** Short description for messages, e.g. the file path. */
    String describe();
}
