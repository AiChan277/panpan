package app.panyaya.model.sales;

/** How the customer paid. No payment gateway is integrated; the method is only recorded. */
public enum PaymentMethod {
    CASH("Cash"),
    QRIS("QRIS"),
    DEBIT_CARD("Debit card"),
    BANK_TRANSFER("Bank transfer"),
    OTHER("Other");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** Only cash needs the tendered amount (to compute change). */
    public boolean requiresTenderedAmount() {
        return this == CASH;
    }

    /** "Other" needs a short reference text, e.g. "voucher 123". */
    public boolean requiresReference() {
        return this == OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
