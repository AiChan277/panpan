package app.panyaya.exception;

/**
 * A business transaction is not allowed in the current state: paying less than the total, voiding a
 * sale from yesterday, completing a production order that was never started, and so on.
 */
public class InvalidTransactionException extends BusinessException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
