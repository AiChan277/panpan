package app.panyaya.model.waste;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A recorded loss. The estimated loss is calculated once, when the waste is recorded:
 * quantity x unit cost of the lot it came from.
 */
public class WasteRecord extends BaseEntity {

    public static final int MIN_NOTE_FOR_OTHER = 5;

    private String wasteNo;
    private Instant occurredAt;
    private LocalDate businessDate;
    private StockKind stockKind;
    private long itemId;
    private String itemName;
    /** Lot or batch id; null for a failed production batch that never entered stock. */
    private Long lotId;
    private String lotCode;
    private BigDecimal quantity;
    private Unit unit;
    private WasteReason reason;
    private String note;
    private BigDecimal unitCost;
    private BigDecimal estimatedLoss;
    private Long productionOrderId;
    private long recordedBy;

    private WasteRecord() {
    }

    public WasteRecord(long id, String wasteNo, Instant occurredAt, LocalDate businessDate, StockKind stockKind,
                       long itemId, String itemName, Long lotId, String lotCode, BigDecimal quantity, Unit unit,
                       WasteReason reason, String note, BigDecimal unitCost, Long productionOrderId, long recordedBy)
            throws ValidationException {
        super(id);
        if (!Quantities.isPositive(quantity)) {
            throw new ValidationException("Waste quantity must be greater than zero.");
        }
        if (reason == null) {
            throw new ValidationException("Please choose a waste reason.");
        }
        String cleanNote = TextUtil.normalize(note);
        if (reason == WasteReason.OTHER && cleanNote.length() < MIN_NOTE_FOR_OTHER) {
            throw new ValidationException("Reason \"Other\" needs a note of at least " + MIN_NOTE_FOR_OTHER + " characters.");
        }
        this.wasteNo = wasteNo;
        this.occurredAt = occurredAt;
        this.businessDate = businessDate;
        this.stockKind = stockKind;
        this.itemId = itemId;
        this.itemName = itemName;
        this.lotId = lotId;
        this.lotCode = lotCode;
        this.quantity = Quantities.normalize(quantity);
        this.unit = unit;
        this.reason = reason;
        this.note = cleanNote;
        this.unitCost = Money.normalize(unitCost);
        this.estimatedLoss = Money.multiply(this.unitCost, this.quantity);
        this.productionOrderId = productionOrderId;
        this.recordedBy = recordedBy;
    }

    /**
     * Factory for a whole failed production batch: the loss is the full material cost, not
     * quantity x unit cost, because no finished batch was ever created.
     */
    public static WasteRecord forFailedProduction(long id, String wasteNo, Instant at, LocalDate businessDate,
                                                  long productId, String productName, int plannedQuantity,
                                                  BigDecimal materialCost, String note, long productionOrderId,
                                                  long userId) throws ValidationException {
        BigDecimal qty = Quantities.of(plannedQuantity);
        BigDecimal unitCost = Money.divide(materialCost, qty);
        WasteRecord record = new WasteRecord(id, wasteNo, at, businessDate, StockKind.PRODUCT, productId, productName,
                null, null, qty, Unit.PCS, WasteReason.PRODUCTION_FAILURE, note, unitCost, productionOrderId, userId);
        record.estimatedLoss = Money.normalize(materialCost); // exact, no rounding drift
        return record;
    }

    public String getWasteNo() {
        return wasteNo;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public StockKind getStockKind() {
        return stockKind;
    }

    public long getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public Long getLotId() {
        return lotId;
    }

    public String getLotCode() {
        return lotCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public WasteReason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getEstimatedLoss() {
        return estimatedLoss;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }

    public long getRecordedBy() {
        return recordedBy;
    }
}
