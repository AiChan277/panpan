package app.panyaya.model.sales;

import app.panyaya.model.Money;
import app.panyaya.model.Quantities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One product line of a completed sale. Name, SKU and price are <b>copies</b> taken at checkout, so
 * renaming a product or changing its price later never changes old receipts or reports.
 */
public class SaleLine {

    private long productId;
    private String skuSnapshot;
    private String nameSnapshot;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private BigDecimal costTotal = Money.ZERO;
    private List<SaleAllocation> allocations = new ArrayList<>();

    private SaleLine() {
    }

    public SaleLine(long productId, String sku, String name, int quantity, BigDecimal unitPrice) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Sale quantity must be a positive whole number");
        }
        this.productId = productId;
        this.skuSnapshot = sku;
        this.nameSnapshot = name;
        this.quantity = quantity;
        this.unitPrice = Money.normalize(unitPrice);
        this.lineTotal = Money.multiply(this.unitPrice, Quantities.of(quantity));
    }

    public void addAllocation(SaleAllocation allocation) {
        allocations.add(allocation);
        costTotal = Money.add(costTotal, Money.multiply(allocation.getUnitCost(), allocation.getQuantity()));
    }

    /** Sum of allocated quantities; must equal {@link #getQuantity()}. */
    public BigDecimal getAllocatedQuantity() {
        BigDecimal total = Quantities.ZERO;
        for (SaleAllocation allocation : allocations) {
            total = total.add(allocation.getQuantity());
        }
        return total;
    }

    public long getProductId() {
        return productId;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getCostTotal() {
        return costTotal;
    }

    public List<SaleAllocation> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }
}
