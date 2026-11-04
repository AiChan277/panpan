package app.panyaya.model.inventory;

import app.panyaya.exception.InsufficientStockException;
import app.panyaya.model.Quantities;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which lots to take stock from, using FEFO: <b>F</b>irst <b>E</b>xpired, <b>F</b>irst
 * <b>O</b>ut. Lots that expire soonest are used first; lots without expiry come last; ties are
 * broken by arrival time and then by id.
 *
 * <p>Course topic <b>Generics</b>: the same class works for {@code FefoAllocator<IngredientLot>}
 * (production) and {@code FefoAllocator<FinishedGoodsBatch>} (sales and waste). The bound
 * {@code T extends StockLot} lets the code call lot methods while keeping the concrete type in the
 * result.</p>
 */
public class FefoAllocator<T extends StockLot> {

    /** Usable lots sorted in FEFO order. Expired and empty lots are skipped. */
    public List<T> usableLotsInOrder(List<T> lots, Instant now, ZoneId zone) {
        List<T> usable = new ArrayList<>();
        for (T lot : lots) {
            if (lot.isUsableAt(now, zone)) {
                usable.add(lot);
            }
        }
        usable.sort(fefoOrder(zone));
        return usable;
    }

    /** Total quantity that could be allocated right now. */
    public BigDecimal available(List<T> lots, Instant now, ZoneId zone) {
        BigDecimal total = Quantities.ZERO;
        for (T lot : usableLotsInOrder(lots, now, zone)) {
            total = total.add(lot.getRemainingQuantity());
        }
        return total;
    }

    /**
     * Splits the needed quantity across lots in FEFO order.
     *
     * @throws InsufficientStockException if the usable lots do not hold enough
     */
    public List<Allocation<T>> allocate(List<T> lots, BigDecimal needed, Instant now, ZoneId zone)
            throws InsufficientStockException {
        if (!Quantities.isPositive(needed)) {
            throw new IllegalArgumentException("Needed quantity must be positive");
        }
        BigDecimal available = available(lots, now, zone);
        if (available.compareTo(needed) < 0) {
            throw new InsufficientStockException("Need " + Formatters.quantity(needed) + " but only "
                    + Formatters.quantity(available) + " is available");
        }

        List<Allocation<T>> result = new ArrayList<>();
        BigDecimal stillNeeded = needed;
        for (T lot : usableLotsInOrder(lots, now, zone)) {
            if (stillNeeded.signum() == 0) {
                break;
            }
            BigDecimal take = Quantities.min(lot.getRemainingQuantity(), stillNeeded);
            result.add(new Allocation<>(lot, take));
            stillNeeded = stillNeeded.subtract(take);
        }
        assert stillNeeded.signum() == 0 : "allocations must add up to the needed quantity";
        return result;
    }

    /** Comparator: expiry ascending (no expiry last), then arrival time, then id. */
    private Comparator<T> fefoOrder(ZoneId zone) {
        return (a, b) -> {
            Instant expiryA = a.getExpirySortKey(zone);
            Instant expiryB = b.getExpirySortKey(zone);
            if (expiryA != null && expiryB == null) {
                return -1;
            }
            if (expiryA == null && expiryB != null) {
                return 1;
            }
            if (expiryA != null) {
                int byExpiry = expiryA.compareTo(expiryB);
                if (byExpiry != 0) {
                    return byExpiry;
                }
            }
            int byArrival = a.getReceivedAt().compareTo(b.getReceivedAt());
            if (byArrival != 0) {
                return byArrival;
            }
            return Long.compare(a.getId(), b.getId());
        };
    }
}
