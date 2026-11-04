package app.panyaya.exception;

/**
 * Reading or writing a data file failed (disk full, file locked, folder deleted, ...).
 * When thrown during a save, the previous data file is still intact and memory is rolled back.
 */
public class PersistenceException extends PanyaException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
