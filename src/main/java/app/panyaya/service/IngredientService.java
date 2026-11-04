package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.StockReceipt;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ingredient master data and per-ingredient stock overview. */
public class IngredientService extends BaseService {

    public IngredientService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    public List<Ingredient> search(String text, boolean includeInactive) {
        List<Ingredient> result = new ArrayList<>();
        for (Ingredient ingredient : data().getIngredients()) {
            boolean matches = TextUtil.containsIgnoreCase(ingredient.getName(), text)
                    || TextUtil.containsIgnoreCase(ingredient.getCode(), text);
            if (matches && (includeInactive || ingredient.isActive())) {
                result.add(ingredient);
            }
        }
        result.sort(Comparator.comparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public List<Ingredient> getActiveIngredients() {
        return search("", false);
    }

    public Ingredient getIngredient(long ingredientId) {
        return BakeryData.findById(data().getIngredients(), ingredientId);
    }

    /**
     * Creates an ingredient. Optionally records opening stock ("initial quantity"), which becomes
     * a normal lot with a stock-receipt movement, so the ledger explains it.
     */
    public Ingredient create(String code, String name, Unit baseUnit, BigDecimal minimumStock, boolean tracksExpiry,
                             boolean stockTracked, Long defaultSupplierId, String notes,
                             BigDecimal openingQuantity, BigDecimal openingTotalCost, LocalDate openingExpiry)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_INGREDIENTS);
        return store.execute("CREATE_INGREDIENT", data -> {
            ensureCodeFree(data, code, 0);
            Ingredient ingredient = new Ingredient(data.nextId(EntityTypes.INGREDIENT), code, name, baseUnit,
                    minimumStock, tracksExpiry, stockTracked, defaultSupplierId, notes);
            data.getIngredients().add(ingredient);
            if (Quantities.isPositive(openingQuantity)) {
                addOpeningStock(data, ingredient, openingQuantity, openingTotalCost, openingExpiry);
            }
            audit(data, "CREATE_INGREDIENT", ingredient.getCode() + " " + ingredient.getName());
            return ingredient;
        });
    }

    private void addOpeningStock(BakeryData data, Ingredient ingredient, BigDecimal quantity, BigDecimal totalCost,
                                 LocalDate expiry) throws BusinessException {
        if (!ingredient.isStockTracked()) {
            throw new ValidationException("\"" + ingredient.getName() + "\" is not stock-tracked, so it has no opening stock.");
        }
        if (ingredient.isTracksExpiry() && expiry == null) {
            throw new ValidationException("Opening stock of \"" + ingredient.getName() + "\" needs an expiry date.");
        }
        if (expiry != null && expiry.isBefore(today())) {
            throw new ValidationException("Opening stock cannot already be expired.");
        }
        BigDecimal cost = totalCost == null ? Money.ZERO : Money.normalize(totalCost);
        if (Money.isNegative(cost)) {
            throw new ValidationException("Opening stock cost cannot be negative.");
        }
        BigDecimal unitCost = Money.divide(cost, quantity);
        LocalDate date = today();
        IngredientLot lot = new IngredientLot(data.nextId(EntityTypes.INGREDIENT_LOT), ingredient.getId(),
                data.nextNumber("L", date), quantity, unitCost, now(), expiry, null, 0);
        data.getIngredientLots().add(lot);
        post(data, new StockReceipt(data.nextId(EntityTypes.MOVEMENT), now(), date, lot, quantity, userId(),
                "Opening stock", 0), lot);
    }

    public Ingredient update(long ingredientId, String code, String name, BigDecimal minimumStock, boolean tracksExpiry,
                             boolean stockTracked, Long defaultSupplierId, String notes)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_INGREDIENTS);
        return store.execute("UPDATE_INGREDIENT", data -> {
            ensureCodeFree(data, code, ingredientId);
            Ingredient ingredient = BakeryData.require(data.getIngredients(), ingredientId, "Ingredient");
            if (!stockTracked && ingredient.isStockTracked()
                    && StockQueries.usableQuantity(data, ingredientId, now(), zone()).signum() > 0) {
                throw new ValidationException("Cannot stop tracking stock while lots still hold stock.");
            }
            ingredient.update(code, name, minimumStock, tracksExpiry, stockTracked, defaultSupplierId, notes);
            audit(data, "UPDATE_INGREDIENT", ingredient.getCode());
            return ingredient;
        });
    }

    public void setActive(long ingredientId, boolean active) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_INGREDIENTS);
        store.execute("INGREDIENT_ACTIVE", data -> {
            BakeryData.require(data.getIngredients(), ingredientId, "Ingredient").setActive(active);
            return null;
        });
    }

    /** Stock overview for one ingredient, computed from its lots. */
    public static IngredientStock stockOf(BakeryData data, Ingredient ingredient, Instant now, ZoneId zone) {
        BigDecimal usable = Quantities.ZERO;
        BigDecimal expired = Quantities.ZERO;
        BigDecimal value = Money.ZERO;
        LocalDate nearest = null;
        for (IngredientLot lot : StockQueries.lotsOf(data, ingredient.getId())) {
            if (lot.isDepleted()) {
                continue;
            }
            if (lot.isExpiredAt(now, zone)) {
                expired = expired.add(lot.getRemainingQuantity());
            } else {
                usable = usable.add(lot.getRemainingQuantity());
                value = value.add(lot.getRemainingValue());
                if (lot.getExpiryDate() != null && (nearest == null || lot.getExpiryDate().isBefore(nearest))) {
                    nearest = lot.getExpiryDate();
                }
            }
        }
        return new IngredientStock(ingredient, usable, expired, Money.normalize(value), nearest);
    }

    public List<IngredientStock> stockOverview(String text, boolean includeInactive) {
        List<IngredientStock> result = new ArrayList<>();
        BakeryData data = data();
        for (Ingredient ingredient : search(text, includeInactive)) {
            result.add(stockOf(data, ingredient, now(), zone()));
        }
        return result;
    }

    private static void ensureCodeFree(BakeryData data, String code, long exceptId) throws ValidationException {
        String clean = TextUtil.normalizeCode(code);
        for (Ingredient ingredient : data.getIngredients()) {
            if (ingredient.getId() != exceptId && ingredient.getCode().equalsIgnoreCase(clean)) {
                throw new ValidationException("Ingredient code \"" + clean + "\" is already used.");
            }
        }
    }
}
