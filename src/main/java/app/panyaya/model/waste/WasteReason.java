package app.panyaya.model.waste;

/** Why stock was thrown away. OTHER requires a note of at least 5 characters. */
public enum WasteReason {
    EXPIRED("Expired"),
    DAMAGED("Damaged"),
    QUALITY_REJECTION("Quality rejection"),
    PRODUCTION_FAILURE("Production failure"),
    UNSOLD("Unsold at closing"),
    OTHER("Other");

    private final String label;

    WasteReason(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
