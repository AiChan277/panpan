package app.panyaya.model.inventory;

/** Where a finished-goods batch came from. */
public enum BatchSource {
    PRODUCTION("Production"),
    PURCHASE("Purchase");

    private final String label;

    BatchSource(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
