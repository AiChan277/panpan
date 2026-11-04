package app.panyaya.model.product;

/** How a product enters finished-goods stock. */
public enum ProductType {
    /** Baked in-house from a recipe; stock comes from production. */
    MANUFACTURED("Baked in-house"),
    /** Bought ready-made for resale (e.g. bottled water); stock comes from goods receipts. */
    RESALE("Bought for resale");

    private final String label;

    ProductType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
