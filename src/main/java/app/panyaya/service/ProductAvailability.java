package app.panyaya.service;

import app.panyaya.model.product.Product;

import java.math.BigDecimal;

/** A product with its sellable (non-expired) quantity, for the POS tiles. */
public class ProductAvailability {

    private final Product product;
    private final BigDecimal sellableQuantity;

    public ProductAvailability(Product product, BigDecimal sellableQuantity) {
        this.product = product;
        this.sellableQuantity = sellableQuantity;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getSellableQuantity() {
        return sellableQuantity;
    }

    public int getSellablePieces() {
        return sellableQuantity.intValue();
    }

    public boolean isSoldOut() {
        return sellableQuantity.signum() == 0;
    }
}
