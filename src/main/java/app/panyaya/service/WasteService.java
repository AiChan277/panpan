package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.StockLot;
import app.panyaya.model.inventory.WasteWriteOff;
import app.panyaya.model.product.Product;
import app.panyaya.model.user.Permission;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Recording waste (expired, damaged, unsold...) with its estimated loss. */
public class WasteService extends BaseService {

    public WasteService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    public WasteRecord recordProductWaste(long batchId, BigDecimal quantity, WasteReason reason, String note)
            throws BusinessException, PersistenceException {
        session.require(Permission.RECORD_PRODUCT_WASTE);
        if (quantity != null && !Quantities.isWholeNumber(quantity)) {
            throw new ValidationException("Products are wasted in whole pieces.");
        }
        return store.execute("RECORD_WASTE", data -> {
            FinishedGoodsBatch batch = BakeryData.require(data.getFinishedGoodsBatches(), batchId, "Batch");
            Product product = BakeryData.require(data.getProducts(), batch.getItemId(), "Product");
            return writeOff(data, batch, product.getName(), Unit.PCS, quantity, reason, note);
        });
    }

    public WasteRecord recordIngredientWaste(long lotId, BigDecimal quantity, WasteReason reason, String note)
            throws BusinessException, PersistenceException {
        session.require(Permission.RECORD_INGREDIENT_WASTE);
        return store.execute("RECORD_WASTE", data -> {
            IngredientLot lot = BakeryData.require(data.getIngredientLots(), lotId, "Lot");
            Ingredient ingredient = BakeryData.require(data.getIngredients(), lot.getItemId(), "Ingredient");
            return writeOff(data, lot, ingredient.getName(), ingredient.getBaseUnit(), quantity, reason, note);
        });
    }

    /** Creates the waste record and its write-off movement for one lot. */
    private WasteRecord writeOff(BakeryData data, StockLot lot, String itemName, Unit unit, BigDecimal quantity,
                                 WasteReason reason, String note) throws BusinessException {
        if (!Quantities.isPositive(quantity)) {
            throw new ValidationException("Waste quantity must be greater than zero.");
        }
        if (quantity.compareTo(lot.getRemainingQuantity()) > 0) {
            throw new ValidationException("Only " + Formatters.quantity(lot.getRemainingQuantity(), unit)
                    + " left in " + lot.getLotCode() + "; cannot waste " + Formatters.quantity(quantity, unit) + ".");
        }
        WasteRecord record = new WasteRecord(data.nextId(EntityTypes.WASTE), data.nextNumber("W", today()), now(),
                today(), lot.getStockKind(), lot.getItemId(), itemName, lot.getId(), lot.getLotCode(), quantity, unit,
                reason, note, lot.getUnitCost(), null, userId());
        post(data, new WasteWriteOff(data.nextId(EntityTypes.MOVEMENT), now(), today(), lot, quantity, userId(),
                record.getWasteNo(), record.getId(), record.getNote()), lot);
        data.getWasteRecords().add(record);
        audit(data, "RECORD_WASTE", record.getWasteNo() + " " + itemName + " " + quantity.toPlainString() + " "
                + reason + ", loss " + record.getEstimatedLoss().toPlainString());
        return record;
    }

    /** Loss preview shown before the user confirms: quantity x the lot's unit cost. */
    public BigDecimal previewLoss(StockKind kind, long lotId, BigDecimal quantity) {
        StockLot lot = kind == StockKind.INGREDIENT
                ? BakeryData.findById(data().getIngredientLots(), lotId)
                : BakeryData.findById(data().getFinishedGoodsBatches(), lotId);
        if (lot == null || quantity == null) {
            return Money.ZERO;
        }
        return Money.multiply(lot.getUnitCost(), quantity);
    }

    /**
     * End-of-day candidates: batches with stock that are already expired or expire before the next
     * morning (start of tomorrow + the "near expiry hours" setting, default 06:00).
     */
    public List<FinishedGoodsBatch> endOfDayCandidates() {
        Instant cutoff = today().plusDays(1).atStartOfDay(zone()).toInstant()
                .plus(Duration.ofHours(data().getSettings().getNearExpiryHours()));
        List<FinishedGoodsBatch> result = new ArrayList<>();
        for (FinishedGoodsBatch batch : data().getFinishedGoodsBatches()) {
            if (!batch.isDepleted() && !batch.getExpiresAt().isAfter(cutoff)) {
                result.add(batch);
            }
        }
        result.sort(Comparator.comparing(FinishedGoodsBatch::getExpiresAt));
        return result;
    }

    /** Writes off all chosen batches in one save: expired ones as EXPIRED, the rest as UNSOLD. */
    public List<WasteRecord> writeOffEndOfDay(List<Long> batchIds) throws BusinessException, PersistenceException {
        session.require(Permission.RECORD_PRODUCT_WASTE);
        if (batchIds.isEmpty()) {
            throw new ValidationException("Select at least one batch to write off.");
        }
        return store.execute("END_OF_DAY_WRITE_OFF", data -> {
            List<WasteRecord> records = new ArrayList<>();
            for (Long batchId : batchIds) {
                FinishedGoodsBatch batch = BakeryData.require(data.getFinishedGoodsBatches(), batchId, "Batch");
                if (batch.isDepleted()) {
                    continue;
                }
                Product product = BakeryData.require(data.getProducts(), batch.getItemId(), "Product");
                WasteReason reason = batch.isExpiredAt(now(), zone()) ? WasteReason.EXPIRED : WasteReason.UNSOLD;
                records.add(writeOff(data, batch, product.getName(), Unit.PCS, batch.getRemainingQuantity(), reason,
                        "End-of-day write-off"));
            }
            return records;
        });
    }

    /** Waste history, newest first. */
    public List<WasteRecord> search(LocalDate from, LocalDate to, StockKind kind, WasteReason reason) {
        List<WasteRecord> result = new ArrayList<>();
        for (WasteRecord record : data().getWasteRecords()) {
            if (from != null && record.getBusinessDate().isBefore(from)) {
                continue;
            }
            if (to != null && record.getBusinessDate().isAfter(to)) {
                continue;
            }
            if (kind != null && record.getStockKind() != kind) {
                continue;
            }
            if (reason != null && record.getReason() != reason) {
                continue;
            }
            result.add(record);
        }
        result.sort(Comparator.comparing(WasteRecord::getId).reversed());
        return result;
    }
}
