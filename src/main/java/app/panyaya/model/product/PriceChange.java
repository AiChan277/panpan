package app.panyaya.model.product;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of a product's price history. Immutable once created. */
public class PriceChange {

    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private Instant changedAt;
    private long changedBy;

    private PriceChange() {
        // for JSON reading
    }

    public PriceChange(BigDecimal oldPrice, BigDecimal newPrice, Instant changedAt, long changedBy) {
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public BigDecimal getOldPrice() {
        return oldPrice;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public long getChangedBy() {
        return changedBy;
    }
}
