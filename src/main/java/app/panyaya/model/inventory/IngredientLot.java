package app.panyaya.model.inventory;

import app.panyaya.model.StockKind;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A delivery of one ingredient. Ingredients expire on a calendar date: the lot can be used up to
 * and including its expiry date and is expired from the next day.
 */
public class IngredientLot extends StockLot {

    private LocalDate expiryDate;
    private Long supplierId;
    private long receiptId;

    private IngredientLot() {
    }

    public IngredientLot(long id, long ingredientId, String lotCode, BigDecimal quantity, BigDecimal unitCost,
                         Instant receivedAt, LocalDate expiryDate, Long supplierId, long receiptId) {
        super(id, ingredientId, lotCode, quantity, unitCost, receivedAt);
        this.expiryDate = expiryDate;
        this.supplierId = supplierId;
        this.receiptId = receiptId;
    }

    @Override
    public StockKind getStockKind() {
        return StockKind.INGREDIENT;
    }

    @Override
    public boolean isExpiredAt(Instant now, ZoneId zone) {
        if (expiryDate == null) {
            return false;
        }
        LocalDate today = LocalDate.ofInstant(now, zone);
        return today.isAfter(expiryDate);
    }

    @Override
    public Instant getExpirySortKey(ZoneId zone) {
        return expiryDate == null ? null : expiryDate.atStartOfDay(zone).toInstant();
    }

    @Override
    public String describeExpiry(ZoneId zone) {
        return expiryDate == null ? "no expiry" : Formatters.date(expiryDate);
    }

    /** True when the lot expires within the given number of days (and is not yet expired). */
    public boolean expiresWithinDays(Instant now, ZoneId zone, int days) {
        if (expiryDate == null || isExpiredAt(now, zone)) {
            return false;
        }
        LocalDate limit = LocalDate.ofInstant(now, zone).plusDays(days);
        return !expiryDate.isAfter(limit);
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public long getReceiptId() {
        return receiptId;
    }
}
