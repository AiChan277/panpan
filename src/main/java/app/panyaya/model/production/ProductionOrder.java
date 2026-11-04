package app.panyaya.model.production;

import app.panyaya.exception.InvalidTransactionException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A planned or executed bake of one product with one recipe.
 *
 * <p>The status field is <b>encapsulated</b>: it can only move along the allowed transitions through
 * {@link #start}, {@link #complete}, {@link #fail} and {@link #cancel}; any other order throws
 * {@link InvalidTransactionException}.</p>
 */
public class ProductionOrder extends BaseEntity {

    private String orderNo;
    private long productId;
    private String productName;
    private long recipeId;
    private String recipeName;
    private int recipeVersion;
    private int plannedQuantity;
    private LocalDate plannedDate;
    private ProductionStatus status = ProductionStatus.PLANNED;
    private List<MaterialUsage> materials = new ArrayList<>();
    private BigDecimal materialCost = Money.ZERO;
    private int goodQuantity;
    private int rejectedQuantity;
    private BigDecimal unitCost = Money.ZERO;
    private Long finishedBatchId;
    private String note;
    private Instant createdAt;
    private long createdBy;
    private Instant startedAt;
    private long startedBy;
    private Instant finishedAt;
    private long finishedBy;

    private ProductionOrder() {
    }

    public ProductionOrder(long id, String orderNo, long productId, String productName, long recipeId,
                           String recipeName, int recipeVersion, int plannedQuantity, LocalDate plannedDate,
                           long userId, Instant at) throws ValidationException {
        super(id);
        if (plannedQuantity < 1) {
            throw new ValidationException("Planned quantity must be at least 1.");
        }
        if (plannedDate == null) {
            throw new ValidationException("Planned date is required.");
        }
        this.orderNo = orderNo;
        this.productId = productId;
        this.productName = productName;
        this.recipeId = recipeId;
        this.recipeName = recipeName;
        this.recipeVersion = recipeVersion;
        this.plannedQuantity = plannedQuantity;
        this.plannedDate = plannedDate;
        this.createdBy = userId;
        this.createdAt = at;
    }

    /** PLANNED -> IN_PROGRESS. Ingredients are consumed at this moment (by the service). */
    public void start(List<MaterialUsage> usedMaterials, int recipeVersionUsed, long userId, Instant at)
            throws InvalidTransactionException {
        requireStatus(ProductionStatus.PLANNED, "start");
        this.materials = new ArrayList<>(usedMaterials);
        BigDecimal total = Money.ZERO;
        for (MaterialUsage usage : usedMaterials) {
            total = total.add(usage.getCost());
        }
        this.materialCost = Money.normalize(total);
        this.recipeVersion = recipeVersionUsed;
        this.status = ProductionStatus.IN_PROGRESS;
        this.startedBy = userId;
        this.startedAt = at;
    }

    /**
     * IN_PROGRESS -> COMPLETED. The material cost is spread over every piece that came out of the
     * oven (good + rejected), so rejected pieces show their true cost as waste:
     * unit cost = material cost / (good + rejected), rounded half-up to 4 decimals.
     */
    public void complete(int good, int rejected, long finishedBatch, long userId, Instant at)
            throws InvalidTransactionException {
        requireStatus(ProductionStatus.IN_PROGRESS, "complete");
        if (good < 1) {
            throw new InvalidTransactionException("Good quantity must be at least 1. Use \"Mark failed\" if nothing is usable.");
        }
        if (rejected < 0) {
            throw new InvalidTransactionException("Rejected quantity cannot be negative.");
        }
        this.goodQuantity = good;
        this.rejectedQuantity = rejected;
        this.unitCost = Money.divide(materialCost, Quantities.of(good + rejected));
        this.finishedBatchId = finishedBatch;
        this.status = ProductionStatus.COMPLETED;
        this.finishedBy = userId;
        this.finishedAt = at;
    }

    /** IN_PROGRESS -> FAILED: the whole batch is lost; consumed ingredients stay consumed. */
    public void fail(String reason, long userId, Instant at) throws InvalidTransactionException {
        requireStatus(ProductionStatus.IN_PROGRESS, "mark as failed");
        this.note = reason;
        this.status = ProductionStatus.FAILED;
        this.finishedBy = userId;
        this.finishedAt = at;
    }

    /** PLANNED -> CANCELLED. Planning never touched stock, so nothing needs to be returned. */
    public void cancel(String reason, long userId, Instant at) throws InvalidTransactionException {
        requireStatus(ProductionStatus.PLANNED, "cancel");
        this.note = reason;
        this.status = ProductionStatus.CANCELLED;
        this.finishedBy = userId;
        this.finishedAt = at;
    }

    private void requireStatus(ProductionStatus expected, String action) throws InvalidTransactionException {
        if (status != expected) {
            throw new InvalidTransactionException("Cannot " + action + " order " + orderNo + ": it is "
                    + status + ", expected " + expected + ".");
        }
    }

    /** Loss value of the rejected pieces. */
    public BigDecimal getRejectedCost() {
        return Money.multiply(unitCost, Quantities.of(rejectedQuantity));
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getRecipeId() {
        return recipeId;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public int getRecipeVersion() {
        return recipeVersion;
    }

    public int getPlannedQuantity() {
        return plannedQuantity;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public ProductionStatus getStatus() {
        return status;
    }

    public List<MaterialUsage> getMaterials() {
        return Collections.unmodifiableList(materials);
    }

    public BigDecimal getMaterialCost() {
        return materialCost;
    }

    public int getGoodQuantity() {
        return goodQuantity;
    }

    public int getRejectedQuantity() {
        return rejectedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public Long getFinishedBatchId() {
        return finishedBatchId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getFinishedBy() {
        return finishedBy;
    }
}
