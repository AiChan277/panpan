package app.panyaya.exception;

/** The signed-in user's role does not allow this operation. Checked in the service layer. */
public class AuthorizationException extends BusinessException {

    public AuthorizationException(String message) {
        super(message);
    }
}
