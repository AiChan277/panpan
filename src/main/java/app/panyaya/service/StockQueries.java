package app.panyaya.service;

import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.inventory.FefoAllocator;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.IngredientLot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only stock calculations shared by services, the dashboard and reports. They take the data
 * as a parameter, so they also work on a background copy of the data.
 */
public final class StockQueries {

    private StockQueries() {
    }

    public static List<IngredientLot> lotsOf(BakeryData data, long ingredientId) {
        List<IngredientLot> result = new ArrayList<>();
        for (IngredientLot lot : data.getIngredientLots()) {
            if (lot.getItemId() == ingredientId) {
                result.add(lot);
            }
        }
        return result;
    }

    public static List<FinishedGoodsBatch> batchesOf(BakeryData data, long productId) {
        List<FinishedGoodsBatch> result = new ArrayList<>();
        for (FinishedGoodsBatch batch : data.getFinishedGoodsBatches()) {
            if (batch.getItemId() == productId) {
                result.add(batch);
            }
        }
        return result;
    }

    /** Ingredient quantity that production may use now (not expired). */
    public static BigDecimal usableQuantity(BakeryData data, long ingredientId, Instant now, ZoneId zone) {
        return new FefoAllocator<IngredientLot>().available(lotsOf(data, ingredientId), now, zone);
    }

    /** Ingredient quantity in expired lots (should be written off). */
    public static BigDecimal expiredQuantity(BakeryData data, long ingredientId, Instant now, ZoneId zone) {
        BigDecimal total = Quantities.ZERO;
        for (IngredientLot lot : lotsOf(data, ingredientId)) {
            if (lot.isExpiredAt(now, zone)) {
                total = total.add(lot.getRemainingQuantity());
            }
        }
        return total;
    }

    /** Finished goods that may be sold now (not expired). */
    public static BigDecimal sellableQuantity(BakeryData data, long productId, Instant now, ZoneId zone) {
        return new FefoAllocator<FinishedGoodsBatch>().available(batchesOf(data, productId), now, zone);
    }

    /**
     * Estimated cost per base unit for recipe costing: weighted average of usable lots; if there is
     * no usable stock, the cost of the most recent lot; {@code null} if the ingredient was never
     * received (cost unknown - never silently treated as zero).
     */
    public static BigDecimal estimatedUnitCost(BakeryData data, long ingredientId, Instant now, ZoneId zone) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        IngredientLot latest = null;
        for (IngredientLot lot : lotsOf(data, ingredientId)) {
            if (latest == null || lot.getReceivedAt().isAfter(latest.getReceivedAt())) {
                latest = lot;
            }
            if (lot.isUsableAt(now, zone)) {
                totalValue = totalValue.add(lot.getRemainingValue());
                totalQuantity = totalQuantity.add(lot.getRemainingQuantity());
            }
        }
        if (totalQuantity.signum() > 0) {
            return Money.divide(totalValue, totalQuantity);
        }
        return latest == null ? null : latest.getUnitCost();
    }
}
