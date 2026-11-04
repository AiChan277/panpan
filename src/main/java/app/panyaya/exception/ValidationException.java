package app.panyaya.exception;

/** User input is invalid (empty name, negative price, malformed SKU, ...). */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(message);
    }
}
