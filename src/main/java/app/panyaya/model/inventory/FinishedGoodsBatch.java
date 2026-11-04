package app.panyaya.model.inventory;

import app.panyaya.model.StockKind;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Finished products ready to sell, from one production run or one purchase. Bread expires at an
 * exact moment (completion time + shelf life); at that instant it is no longer sellable.
 */
public class FinishedGoodsBatch extends StockLot {

    private Instant expiresAt;
    private BatchSource source;
    private Long productionOrderId;

    private FinishedGoodsBatch() {
    }

    public FinishedGoodsBatch(long id, long productId, String batchCode, BigDecimal quantity, BigDecimal unitCost,
                              Instant producedAt, Instant expiresAt, BatchSource source, Long productionOrderId) {
        super(id, productId, batchCode, quantity, unitCost, producedAt);
        if (expiresAt == null || !expiresAt.isAfter(producedAt)) {
            throw new IllegalArgumentException("Batch expiry must be after its production time");
        }
        this.expiresAt = expiresAt;
        this.source = source;
        this.productionOrderId = productionOrderId;
    }

    @Override
    public StockKind getStockKind() {
        return StockKind.PRODUCT;
    }

    /** {@code expiresAt == now} already counts as expired. */
    @Override
    public boolean isExpiredAt(Instant now, ZoneId zone) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public Instant getExpirySortKey(ZoneId zone) {
        return expiresAt;
    }

    @Override
    public String describeExpiry(ZoneId zone) {
        return Formatters.dateTime(expiresAt, zone);
    }

    /** True when the batch expires within the given number of hours (and is not yet expired). */
    public boolean expiresWithinHours(Instant now, int hours) {
        return now.isBefore(expiresAt) && !expiresAt.isAfter(now.plus(Duration.ofHours(hours)));
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public BatchSource getSource() {
        return source;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }
}
