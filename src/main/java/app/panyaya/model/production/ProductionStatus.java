package app.panyaya.model.production;

/**
 * Production order lifecycle:
 * PLANNED -> IN_PROGRESS -> COMPLETED or FAILED; PLANNED -> CANCELLED. No other transitions.
 */
public enum ProductionStatus {
    PLANNED("Planned"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String label;

    ProductionStatus(String label) {
        this.label = label;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    @Override
    public String toString() {
        return label;
    }
}
