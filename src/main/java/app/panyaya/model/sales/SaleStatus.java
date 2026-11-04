package app.panyaya.model.sales;

/** OPEN exists only while a checkout is being built in memory; it is never saved. */
public enum SaleStatus {
    OPEN("Open"),
    COMPLETED("Completed"),
    VOIDED("Voided");

    private final String label;

    SaleStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
