package app.panyaya.model.inventory;

import java.math.BigDecimal;

/**
 * "Take this quantity from this lot" - one result line of the {@link FefoAllocator}.
 *
 * @param <T> the lot type (ingredient lot or finished-goods batch)
 */
public class Allocation<T extends StockLot> {

    private final T lot;
    private final BigDecimal quantity;

    public Allocation(T lot, BigDecimal quantity) {
        this.lot = lot;
        this.quantity = quantity;
    }

    public T getLot() {
        return lot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
