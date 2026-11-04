package app.panyaya.model.sales;

import app.panyaya.model.Money;
import app.panyaya.model.Quantities;

import java.math.BigDecimal;

/** One product in the shopping cart (in memory only, never saved). */
public class CartLine {

    private final long productId;
    private final String name;
    private final String sku;
    private final BigDecimal unitPrice;
    private int quantity;

    public CartLine(long productId, String name, String sku, BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLineTotal() {
        return Money.multiply(unitPrice, Quantities.of(quantity));
    }

    public long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
