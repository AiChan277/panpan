package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.AdjustmentReason;
import app.panyaya.model.inventory.BatchSource;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.inventory.StockAdjustment;
import app.panyaya.model.inventory.StockLot;
import app.panyaya.model.inventory.StockReceipt;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.supplier.GoodsReceipt;
import app.panyaya.model.supplier.GoodsReceiptLine;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Receiving goods, manual stock adjustments and stock/ledger queries. */
public class InventoryService extends BaseService {

    public InventoryService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    /**
     * Records a delivery. All lines are saved together or not at all: one lot (ingredient) or batch
     * (resale product) and one stock-receipt movement per line.
     */
    public GoodsReceipt receiveGoods(long supplierId, LocalDate receivedDate, String invoiceNo,
                                     List<ReceiptLineInput> lines) throws BusinessException, PersistenceException {
        session.require(Permission.RECEIVE_STOCK);
        return store.execute("RECEIVE_GOODS", data -> {
            Supplier supplier = BakeryData.require(data.getSuppliers(), supplierId, "Supplier");
            if (receivedDate == null || receivedDate.isAfter(today())) {
                throw new ValidationException("The received date cannot be in the future.");
            }
            if (lines.isEmpty()) {
                throw new ValidationException("Add at least one received item.");
            }
            LocalDate businessDate = today();
            GoodsReceipt receipt = new GoodsReceipt(data.nextId(EntityTypes.RECEIPT),
                    data.nextNumber("GR", businessDate), supplier.getId(), supplier.getName(), receivedDate, now(),
                    TextUtil.normalize(invoiceNo), userId());
            int lineNo = 0;
            for (ReceiptLineInput input : lines) {
                lineNo++;
                if (input.getStockKind() == StockKind.INGREDIENT) {
                    receipt.addLine(receiveIngredient(data, receipt, supplier, input, lineNo, businessDate));
                } else {
                    receipt.addLine(receiveProduct(data, receipt, input, lineNo, businessDate));
                }
            }
            data.getGoodsReceipts().add(receipt);
            audit(data, "RECEIVE_GOODS", receipt.getReceiptNo() + " from " + supplier.getName() + ", "
                    + lines.size() + " line(s)");
            return receipt;
        });
    }

    private GoodsReceiptLine receiveIngredient(BakeryData data, GoodsReceipt receipt, Supplier supplier,
                                               ReceiptLineInput input, int lineNo, LocalDate businessDate)
            throws BusinessException {
        Ingredient ingredient = BakeryData.require(data.getIngredients(), input.getItemId(), "Ingredient");
        String prefix = "Line " + lineNo + " (" + ingredient.getName() + "): ";
        if (!ingredient.isStockTracked()) {
            throw new ValidationException(prefix + "this ingredient is not stock-tracked.");
        }
        validateQuantityAndCost(input, prefix);
        if (!input.getUnit().isCompatibleWith(ingredient.getBaseUnit())) {
            throw new ValidationException(prefix + "unit " + input.getUnit() + " does not fit the stock unit "
                    + ingredient.getBaseUnit() + ".");
        }
        LocalDate expiry = input.getExpiryDate();
        if (ingredient.isTracksExpiry() && expiry == null) {
            throw new ValidationException(prefix + "an expiry date is required.");
        }
        if (expiry != null && expiry.isBefore(receipt.getReceivedDate())) {
            throw new ValidationException(prefix + "expiry date is before the received date.");
        }
        BigDecimal baseQuantity = input.getUnit().convert(input.getQuantity(), ingredient.getBaseUnit());
        BigDecimal totalCost = Money.normalize(input.getTotalCost());
        BigDecimal unitCost = Money.divide(totalCost, baseQuantity);
        String lotCode = TextUtil.isBlank(input.getLotCode()) ? data.nextNumber("L", businessDate)
                : TextUtil.normalizeCode(input.getLotCode());

        IngredientLot lot = new IngredientLot(data.nextId(EntityTypes.INGREDIENT_LOT), ingredient.getId(), lotCode,
                baseQuantity, unitCost, now(), expiry, supplier.getId(), receipt.getId());
        data.getIngredientLots().add(lot);
        post(data, new StockReceipt(data.nextId(EntityTypes.MOVEMENT), now(), businessDate, lot, baseQuantity,
                userId(), receipt.getReceiptNo(), receipt.getId()), lot);
        return new GoodsReceiptLine(StockKind.INGREDIENT, ingredient.getId(), ingredient.getName(),
                Quantities.normalize(input.getQuantity()), input.getUnit(), baseQuantity, totalCost, unitCost,
                lotCode, expiry, lot.getId());
    }

    private GoodsReceiptLine receiveProduct(BakeryData data, GoodsReceipt receipt, ReceiptLineInput input, int lineNo,
                                            LocalDate businessDate) throws BusinessException {
        Product product = BakeryData.require(data.getProducts(), input.getItemId(), "Product");
        String prefix = "Line " + lineNo + " (" + product.getName() + "): ";
        if (product.getType() != ProductType.RESALE) {
            throw new ValidationException(prefix + "only resale products are received from suppliers; "
                    + "baked products enter stock through Production.");
        }
        validateQuantityAndCost(input, prefix);
        if (!input.getUnit().isCompatibleWith(Unit.PCS)) {
            throw new ValidationException(prefix + "products are counted in pieces.");
        }
        BigDecimal pieces = input.getUnit().toBase(input.getQuantity());
        if (!Quantities.isWholeNumber(pieces)) {
            throw new ValidationException(prefix + "the number of pieces must be a whole number.");
        }
        LocalDate expiry = input.getExpiryDate();
        if (expiry == null) {
            throw new ValidationException(prefix + "a best-before date is required for products.");
        }
        if (expiry.isBefore(today())) {
            throw new ValidationException(prefix + "the product is already expired.");
        }
        // sellable until the end of its best-before date
        Instant expiresAt = expiry.plusDays(1).atStartOfDay(zone()).toInstant();
        BigDecimal totalCost = Money.normalize(input.getTotalCost());
        BigDecimal unitCost = Money.divide(totalCost, pieces);
        String batchCode = TextUtil.isBlank(input.getLotCode()) ? data.nextNumber(product.getSku(), businessDate)
                : TextUtil.normalizeCode(input.getLotCode());

        FinishedGoodsBatch batch = new FinishedGoodsBatch(data.nextId(EntityTypes.FG_BATCH), product.getId(),
                batchCode, pieces, unitCost, now(), expiresAt, BatchSource.PURCHASE, null);
        data.getFinishedGoodsBatches().add(batch);
        post(data, new StockReceipt(data.nextId(EntityTypes.MOVEMENT), now(), businessDate, batch, pieces, userId(),
                receipt.getReceiptNo(), receipt.getId()), batch);
        return new GoodsReceiptLine(StockKind.PRODUCT, product.getId(), product.getName(),
                Quantities.normalize(input.getQuantity()), input.getUnit(), pieces, totalCost, unitCost, batchCode,
                expiry, batch.getId());
    }

    private static void validateQuantityAndCost(ReceiptLineInput input, String prefix) throws ValidationException {
        if (!Quantities.isPositive(input.getQuantity())) {
            throw new ValidationException(prefix + "quantity must be greater than zero.");
        }
        if (input.getUnit() == null) {
            throw new ValidationException(prefix + "choose a unit.");
        }
        if (input.getTotalCost() == null || input.getTotalCost().signum() < 0) {
            throw new ValidationException(prefix + "total cost cannot be negative.");
        }
    }

    /** Owner-only correction of one lot after a physical stock count. */
    public InventoryMovement adjustStock(StockKind kind, long lotId, boolean increase, BigDecimal quantity,
                                         AdjustmentReason reason, String note)
            throws BusinessException, PersistenceException {
        session.require(Permission.ADJUST_STOCK);
        return store.execute("ADJUST_STOCK", data -> {
            if (!Quantities.isPositive(quantity)) {
                throw new ValidationException("Adjustment quantity must be greater than zero.");
            }
            if (reason == null) {
                throw new ValidationException("A reason is required for every adjustment.");
            }
            String cleanNote = TextUtil.normalize(note);
            if (cleanNote.length() < 3) {
                throw new ValidationException("Please describe the adjustment (at least 3 characters).");
            }
            StockLot lot = kind == StockKind.INGREDIENT
                    ? BakeryData.require(data.getIngredientLots(), lotId, "Lot")
                    : BakeryData.require(data.getFinishedGoodsBatches(), lotId, "Batch");
            StockAdjustment adjustment = new StockAdjustment(data.nextId(EntityTypes.MOVEMENT), now(), today(), lot,
                    quantity, userId(), "ADJ " + reason.name(), increase, reason, cleanNote);
            post(data, adjustment, lot);
            audit(data, "ADJUST_STOCK", lot.getLotCode() + (increase ? " +" : " -") + quantity.toPlainString()
                    + " " + reason + ": " + cleanNote);
            return adjustment;
        });
    }

    // ---- queries ----------------------------------------------------------------------------

    public List<IngredientLot> getIngredientLots(Long ingredientId, boolean includeEmpty) {
        List<IngredientLot> result = new ArrayList<>();
        for (IngredientLot lot : data().getIngredientLots()) {
            if ((ingredientId == null || lot.getItemId() == ingredientId) && (includeEmpty || !lot.isDepleted())) {
                result.add(lot);
            }
        }
        result.sort(Comparator.comparing(IngredientLot::getReceivedAt).reversed());
        return result;
    }

    public List<FinishedGoodsBatch> getBatches(Long productId, boolean includeEmpty) {
        List<FinishedGoodsBatch> result = new ArrayList<>();
        for (FinishedGoodsBatch batch : data().getFinishedGoodsBatches()) {
            if ((productId == null || batch.getItemId() == productId) && (includeEmpty || !batch.isDepleted())) {
                result.add(batch);
            }
        }
        result.sort(Comparator.comparing(FinishedGoodsBatch::getExpiresAt));
        return result;
    }

    /** Ledger lines, newest first, with optional filters. */
    public List<InventoryMovement> getMovements(StockKind kind, Long itemId, LocalDate from, LocalDate to) {
        List<InventoryMovement> result = new ArrayList<>();
        for (InventoryMovement movement : data().getMovements()) {
            if (kind != null && movement.getStockKind() != kind) {
                continue;
            }
            if (itemId != null && movement.getItemId() != itemId) {
                continue;
            }
            if (from != null && movement.getBusinessDate().isBefore(from)) {
                continue;
            }
            if (to != null && movement.getBusinessDate().isAfter(to)) {
                continue;
            }
            result.add(movement);
        }
        result.sort(Comparator.comparing(InventoryMovement::getId).reversed());
        return result;
    }

    public List<GoodsReceipt> getReceipts() {
        List<GoodsReceipt> result = new ArrayList<>(data().getGoodsReceipts());
        result.sort(Comparator.comparing(GoodsReceipt::getRecordedAt).reversed());
        return result;
    }

    public String itemName(StockKind kind, long itemId) {
        if (kind == StockKind.INGREDIENT) {
            Ingredient ingredient = BakeryData.findById(data().getIngredients(), itemId);
            return ingredient == null ? "?" : ingredient.getName();
        }
        Product product = BakeryData.findById(data().getProducts(), itemId);
        return product == null ? "?" : product.getName();
    }

    public String lotCode(StockKind kind, long lotId) {
        StockLot lot = kind == StockKind.INGREDIENT
                ? BakeryData.findById(data().getIngredientLots(), lotId)
                : BakeryData.findById(data().getFinishedGoodsBatches(), lotId);
        return lot == null ? "?" : lot.getLotCode();
    }

    public Unit unitOf(StockKind kind, long itemId) {
        if (kind == StockKind.PRODUCT) {
            return Unit.PCS;
        }
        Ingredient ingredient = BakeryData.findById(data().getIngredients(), itemId);
        return ingredient == null ? Unit.G : ingredient.getBaseUnit();
    }

    public BigDecimal sellableQuantity(long productId) {
        return StockQueries.sellableQuantity(data(), productId, now(), zone());
    }
}
