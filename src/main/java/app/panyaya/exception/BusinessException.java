package app.panyaya.exception;

/**
 * A business rule was violated (e.g. "not enough flour"). Nothing is saved when this is thrown:
 * {@link app.panyaya.persistence.DataStore#execute} rolls the in-memory data back.
 */
public class BusinessException extends PanyaException {

    public BusinessException(String message) {
        super(message);
    }
}
