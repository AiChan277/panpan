package app.panyaya.model;

/** Which kind of stock a lot, movement or waste record belongs to. */
public enum StockKind {
    /** Raw material lot (flour, butter, ...). */
    INGREDIENT("Ingredient"),
    /** Finished goods batch (bread, cakes, resale drinks, ...). */
    PRODUCT("Product");

    private final String label;

    StockKind(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
