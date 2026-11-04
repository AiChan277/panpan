package app.panyaya.model.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Stock removed as waste (expired, damaged, unsold...). Unlike sales and production, waste is
 * allowed on expired stock: writing it off is exactly what should happen to it, so this class keeps
 * the default (permissive) {@code checkAllowed}.
 */
public class WasteWriteOff extends InventoryMovement {

    private long wasteRecordId;

    private WasteWriteOff() {
    }

    public WasteWriteOff(long id, Instant at, LocalDate businessDate, StockLot lot, BigDecimal quantity,
                         long userId, String reference, long wasteRecordId, String note) {
        super(id, at, businessDate, lot, quantity, userId, reference, note);
        this.wasteRecordId = wasteRecordId;
    }

    @Override
    public boolean isIncoming() {
        return false;
    }

    @Override
    public String getTypeName() {
        return "Waste";
    }

    public long getWasteRecordId() {
        return wasteRecordId;
    }
}
