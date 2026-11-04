package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.InsufficientStockException;
import app.panyaya.exception.InvalidRecipeException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.inventory.Allocation;
import app.panyaya.model.inventory.BatchSource;
import app.panyaya.model.inventory.FefoAllocator;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.ProductionConsumption;
import app.panyaya.model.inventory.ProductionOutput;
import app.panyaya.model.product.Product;
import app.panyaya.model.production.MaterialUsage;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.production.ProductionStatus;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.user.Permission;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.Formatters;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Production workflow: plan -> start (ingredients consumed, FEFO) -> complete (finished goods
 * added, rejected pieces recorded as waste) or fail; a plan can also be cancelled.
 */
public class ProductionService extends BaseService {

    private final FefoAllocator<IngredientLot> allocator = new FefoAllocator<>();

    public ProductionService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    /** Planning never changes stock. */
    public ProductionOrder plan(long productId, long recipeId, int quantity, LocalDate plannedDate)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTION);
        return store.execute("PLAN_PRODUCTION", data -> {
            Product product = BakeryData.require(data.getProducts(), productId, "Product");
            if (!product.isActive() || !product.isManufactured()) {
                throw new ValidationException("\"" + product.getName() + "\" cannot be produced (archived or resale).");
            }
            Recipe recipe = BakeryData.require(data.getRecipes(), recipeId, "Recipe");
            if (recipe.getProductId() != productId) {
                throw new InvalidRecipeException("Recipe \"" + recipe.getName() + "\" does not make " + product.getName() + ".");
            }
            if (!recipe.isActive()) {
                throw new InvalidRecipeException("Recipe \"" + recipe.getName() + "\" is not active yet. Activate it first.");
            }
            if (plannedDate != null && plannedDate.isBefore(today())) {
                throw new ValidationException("The planned date cannot be in the past.");
            }
            ProductionOrder order = new ProductionOrder(data.nextId(EntityTypes.PRODUCTION),
                    data.nextNumber("PRD", today()), product.getId(), product.getName(), recipe.getId(),
                    recipe.getName(), recipe.getVersion(), quantity, plannedDate, userId(), now());
            data.getProductionOrders().add(order);
            audit(data, "PLAN_PRODUCTION", order.getOrderNo() + " " + quantity + " x " + product.getName());
            return order;
        });
    }

    /** Requirements of a planned order against current stock (advisory while planning). */
    public List<MaterialRequirement> requirementsFor(long orderId) throws ValidationException {
        ProductionOrder order = BakeryData.require(data().getProductionOrders(), orderId, "Production order");
        Recipe recipe = BakeryData.require(data().getRecipes(), order.getRecipeId(), "Recipe");
        return RecipeService.calculateRequirements(data(), recipe, Quantities.of(order.getPlannedQuantity()), now(), zone());
    }

    /**
     * Starts production: re-checks stock against the newest data and consumes all stock-tracked
     * ingredients FEFO. If anything is short, nothing at all is consumed and the full shortage
     * list is reported.
     */
    public ProductionOrder start(long orderId) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTION);
        return store.execute("START_PRODUCTION", data -> {
            ProductionOrder order = BakeryData.require(data.getProductionOrders(), orderId, "Production order");
            if (order.getStatus() != ProductionStatus.PLANNED) {
                throw new app.panyaya.exception.InvalidTransactionException("Order " + order.getOrderNo()
                        + " is " + order.getStatus() + " and cannot be started.");
            }
            Recipe recipe = BakeryData.require(data.getRecipes(), order.getRecipeId(), "Recipe");
            if (!recipe.isActive()) {
                throw new InvalidRecipeException("Recipe \"" + recipe.getName() + "\" is no longer active.");
            }
            Map<Long, Ingredient> ingredients = BakeryData.indexById(data.getIngredients());
            recipe.validateForProduction(ingredients);
            Map<Long, BigDecimal> required = recipe.scaleTo(Quantities.of(order.getPlannedQuantity()), ingredients);

            // 1) check everything first, so the baker sees all shortages at once
            List<String> shortages = new ArrayList<>();
            for (Map.Entry<Long, BigDecimal> entry : required.entrySet()) {
                Ingredient ingredient = ingredients.get(entry.getKey());
                if (!ingredient.isStockTracked()) {
                    continue;
                }
                BigDecimal available = allocator.available(StockQueries.lotsOf(data, ingredient.getId()), now(), zone());
                if (available.compareTo(entry.getValue()) < 0) {
                    shortages.add(ingredient.getName() + ": short by "
                            + Formatters.quantity(entry.getValue().subtract(available), ingredient.getBaseUnit())
                            + " (need " + Formatters.quantity(entry.getValue(), ingredient.getBaseUnit())
                            + ", usable " + Formatters.quantity(available, ingredient.getBaseUnit()) + ")");
                }
            }
            if (!shortages.isEmpty()) {
                throw new InsufficientStockException(shortages);
            }

            // 2) consume FEFO and remember what was really used
            List<MaterialUsage> materials = new ArrayList<>();
            for (Map.Entry<Long, BigDecimal> entry : required.entrySet()) {
                Ingredient ingredient = ingredients.get(entry.getKey());
                if (!ingredient.isStockTracked()) {
                    materials.add(new MaterialUsage(ingredient.getId(), ingredient.getName(), entry.getValue(),
                            ingredient.getBaseUnit(), Money.ZERO, false));
                    continue;
                }
                BigDecimal cost = Money.ZERO;
                List<Allocation<IngredientLot>> allocations =
                        allocator.allocate(StockQueries.lotsOf(data, ingredient.getId()), entry.getValue(), now(), zone());
                for (Allocation<IngredientLot> allocation : allocations) {
                    IngredientLot lot = allocation.getLot();
                    post(data, new ProductionConsumption(data.nextId(EntityTypes.MOVEMENT), now(), today(), lot,
                            allocation.getQuantity(), userId(), order.getOrderNo(), order.getId()), lot);
                    cost = cost.add(Money.multiply(lot.getUnitCost(), allocation.getQuantity()));
                }
                materials.add(new MaterialUsage(ingredient.getId(), ingredient.getName(), entry.getValue(),
                        ingredient.getBaseUnit(), Money.normalize(cost), true));
            }
            order.start(materials, recipe.getVersion(), userId(), now());
            audit(data, "START_PRODUCTION", order.getOrderNo() + " material cost " + order.getMaterialCost().toPlainString());
            return order;
        });
    }

    /** Completes production: good pieces become a sellable batch, rejected pieces become waste. */
    public ProductionOrder complete(long orderId, int goodQuantity, int rejectedQuantity)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTION);
        return store.execute("COMPLETE_PRODUCTION", data -> {
            ProductionOrder order = BakeryData.require(data.getProductionOrders(), orderId, "Production order");
            Product product = BakeryData.require(data.getProducts(), order.getProductId(), "Product");
            Recipe recipe = BakeryData.findById(data.getRecipes(), order.getRecipeId());
            int shelfLife = recipe != null && recipe.getShelfLifeHours() != null
                    ? recipe.getShelfLifeHours() : product.getShelfLifeHours();

            long batchId = data.nextId(EntityTypes.FG_BATCH);
            order.complete(goodQuantity, rejectedQuantity, batchId, userId(), now());

            Instant producedAt = now();
            Instant expiresAt = producedAt.plus(Duration.ofHours(shelfLife));
            FinishedGoodsBatch batch = new FinishedGoodsBatch(batchId, product.getId(),
                    data.nextNumber(product.getSku(), today()), Quantities.of(goodQuantity), order.getUnitCost(),
                    producedAt, expiresAt, BatchSource.PRODUCTION, order.getId());
            data.getFinishedGoodsBatches().add(batch);
            post(data, new ProductionOutput(data.nextId(EntityTypes.MOVEMENT), now(), today(), batch,
                    Quantities.of(goodQuantity), userId(), order.getOrderNo(), order.getId()), batch);

            if (rejectedQuantity > 0) {
                WasteRecord waste = new WasteRecord(data.nextId(EntityTypes.WASTE), data.nextNumber("W", today()),
                        now(), today(), app.panyaya.model.StockKind.PRODUCT, product.getId(), product.getName(),
                        null, batch.getLotCode(), Quantities.of(rejectedQuantity), app.panyaya.model.Unit.PCS,
                        WasteReason.PRODUCTION_FAILURE, "Rejected at completion of " + order.getOrderNo(),
                        order.getUnitCost(), order.getId(), userId());
                data.getWasteRecords().add(waste);
            }
            audit(data, "COMPLETE_PRODUCTION", order.getOrderNo() + " good " + goodQuantity + ", rejected " + rejectedQuantity);
            return order;
        });
    }

    /** The whole batch was lost (burnt, dropped...). Consumed ingredients stay consumed. */
    public ProductionOrder markFailed(long orderId, String reason) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTION);
        return store.execute("FAIL_PRODUCTION", data -> {
            ProductionOrder order = BakeryData.require(data.getProductionOrders(), orderId, "Production order");
            String cleanReason = TextUtil.normalize(reason);
            if (cleanReason.length() < 3) {
                throw new ValidationException("Please describe what went wrong (at least 3 characters).");
            }
            order.fail(cleanReason, userId(), now());
            data.getWasteRecords().add(WasteRecord.forFailedProduction(data.nextId(EntityTypes.WASTE),
                    data.nextNumber("W", today()), now(), today(), order.getProductId(), order.getProductName(),
                    order.getPlannedQuantity(), order.getMaterialCost(), cleanReason, order.getId(), userId()));
            audit(data, "FAIL_PRODUCTION", order.getOrderNo() + ": " + cleanReason);
            return order;
        });
    }

    public ProductionOrder cancel(long orderId, String reason) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTION);
        return store.execute("CANCEL_PRODUCTION", data -> {
            ProductionOrder order = BakeryData.require(data.getProductionOrders(), orderId, "Production order");
            order.cancel(TextUtil.normalize(reason), userId(), now());
            audit(data, "CANCEL_PRODUCTION", order.getOrderNo());
            return order;
        });
    }

    /** Orders with optional filters, newest first. */
    public List<ProductionOrder> search(LocalDate from, LocalDate to, Long productId, ProductionStatus status) {
        List<ProductionOrder> result = new ArrayList<>();
        for (ProductionOrder order : data().getProductionOrders()) {
            if (from != null && order.getPlannedDate().isBefore(from)) {
                continue;
            }
            if (to != null && order.getPlannedDate().isAfter(to)) {
                continue;
            }
            if (productId != null && order.getProductId() != productId) {
                continue;
            }
            if (status != null && order.getStatus() != status) {
                continue;
            }
            result.add(order);
        }
        result.sort(Comparator.comparing(ProductionOrder::getId).reversed());
        return result;
    }

    public ProductionOrder getOrder(long orderId) {
        return BakeryData.findById(data().getProductionOrders(), orderId);
    }
}
