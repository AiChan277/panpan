package app.panyaya.exception;

/** A recipe cannot be activated or used (no items, zero yield, inactive ingredient, ...). */
public class InvalidRecipeException extends BusinessException {

    public InvalidRecipeException(String message) {
        super(message);
    }
}
