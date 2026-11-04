package app.panyaya.model.inventory;

/** Mandatory reason for a manual stock adjustment. */
public enum AdjustmentReason {
    COUNT_CORRECTION("Stock count correction"),
    DATA_ENTRY_ERROR("Data entry error"),
    FOUND("Stock found"),
    THEFT_OR_LOSS("Theft or loss"),
    OTHER("Other");

    private final String label;

    AdjustmentReason(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
