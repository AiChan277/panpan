package app.panyaya.model.sales;

import app.panyaya.exception.InvalidTransactionException;
import app.panyaya.model.Money;
import app.panyaya.util.Formatters;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;

/** Payment details entered in the payment dialog. */
public class PaymentInfo {

    public static final int MAX_REFERENCE_LENGTH = 60;

    private final PaymentMethod method;
    private final BigDecimal tendered;
    private final String reference;

    public PaymentInfo(PaymentMethod method, BigDecimal tendered, String reference) {
        this.method = method;
        this.tendered = tendered == null ? null : Money.normalize(tendered);
        this.reference = TextUtil.normalize(reference);
    }

    /** Checks the payment against the amount due. */
    public void validate(BigDecimal total) throws InvalidTransactionException {
        if (method == null) {
            throw new InvalidTransactionException("Please choose a payment method.");
        }
        if (method.requiresTenderedAmount()) {
            if (tendered == null || tendered.compareTo(total) < 0) {
                throw new InvalidTransactionException("Cash received (" + Formatters.money(tendered)
                        + ") is less than the total (" + Formatters.money(total) + ").");
            }
        }
        if (method.requiresReference() && reference.isEmpty()) {
            throw new InvalidTransactionException("Payment method \"Other\" needs a reference.");
        }
        if (reference.length() > MAX_REFERENCE_LENGTH) {
            throw new InvalidTransactionException("Payment reference is too long (max " + MAX_REFERENCE_LENGTH + ").");
        }
    }

    /** Change to give back: tendered - total for cash, zero otherwise. */
    public BigDecimal changeFor(BigDecimal total) {
        if (method != null && method.requiresTenderedAmount() && tendered != null) {
            return Money.normalize(tendered.subtract(total));
        }
        return Money.ZERO;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getTendered() {
        return tendered;
    }

    public String getReference() {
        return reference;
    }
}
