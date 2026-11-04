package app.panyaya.exception;

/**
 * Root of all Pan-ya Ya exceptions.
 *
 * <p>Course topic <b>Exceptions</b>: these are <i>checked</i> exceptions, so the compiler forces
 * every caller to either handle them or declare them with {@code throws}. The UI catches them and
 * shows {@link #getMessage()} to the user in plain language.</p>
 */
public abstract class PanyaException extends Exception {

    protected PanyaException(String message) {
        super(message);
    }

    protected PanyaException(String message, Throwable cause) {
        super(message, cause);
    }
}
