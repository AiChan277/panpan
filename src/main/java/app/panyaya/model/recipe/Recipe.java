package app.panyaya.model.recipe;

import app.panyaya.exception.InvalidRecipeException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Quantities;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How to make one product: a list of ingredients and the number of pieces the list yields.
 *
 * <p>Recipes are data. "Japanese Milk Bread" is a Recipe object created on the Recipes screen and
 * saved to JSON - there is no JapaneseMilkBread class. Starter templates are ordinary recipes
 * imported from a JSON resource and marked {@code template = true} until reviewed.</p>
 *
 * <p>History stays correct when a recipe is edited: every edit increases {@link #getVersion()},
 * and production orders copy the material quantities and costs they actually used.</p>
 */
public class Recipe extends BaseEntity {

    private String name;
    private long productId;
    private BigDecimal yieldQuantity;
    private List<RecipeItem> items = new ArrayList<>();
    private String instructions;
    private int prepMinutes;
    private int bakeMinutes;
    /** Optional: overrides the product's default shelf life. */
    private Integer shelfLifeHours;
    private RecipeStatus status = RecipeStatus.DRAFT;
    /** True for starter templates that the owner has not reviewed yet. */
    private boolean template;
    private int version = 1;
    private Instant createdAt;
    private Instant updatedAt;
    private long updatedBy;

    private Recipe() {
    }

    /** Creates a new recipe (version 1, DRAFT). */
    public Recipe(long id, String name, long productId, BigDecimal yieldQuantity, List<RecipeItem> items,
                  int prepMinutes, int bakeMinutes, Integer shelfLifeHours, String instructions,
                  Instant createdAt, long userId) throws ValidationException {
        super(id);
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.updatedBy = userId;
        setBasics(name, productId, yieldQuantity);
        setContent(items, prepMinutes, bakeMinutes, shelfLifeHours, instructions);
    }

    private void setBasics(String newName, long newProductId, BigDecimal newYield) throws ValidationException {
        String cleanName = TextUtil.normalize(newName);
        if (cleanName.isEmpty() || cleanName.length() > 80) {
            throw new ValidationException("Recipe name must be 1-80 characters.");
        }
        if (newProductId <= 0) {
            throw new ValidationException("Please choose the product this recipe makes.");
        }
        if (!Quantities.isPositive(newYield)) {
            throw new ValidationException("Yield must be greater than zero.");
        }
        this.name = cleanName;
        this.productId = newProductId;
        this.yieldQuantity = Quantities.normalize(newYield);
    }

    /**
     * Saves an edit from the recipe editor. Archived recipes cannot be edited. Every edit creates
     * a new version number.
     */
    public void update(String newName, long newProductId, BigDecimal newYield, List<RecipeItem> newItems,
                       int newPrepMinutes, int newBakeMinutes, Integer newShelfLifeHours, String newInstructions,
                       long userId, Instant at) throws ValidationException {
        if (status == RecipeStatus.ARCHIVED) {
            throw new ValidationException("Archived recipes cannot be edited. Duplicate it instead.");
        }
        setBasics(newName, newProductId, newYield);
        setContent(newItems, newPrepMinutes, newBakeMinutes, newShelfLifeHours, newInstructions);
        this.version++;
        this.updatedAt = at;
        this.updatedBy = userId;
    }

    private void setContent(List<RecipeItem> newItems, int newPrepMinutes, int newBakeMinutes,
                            Integer newShelfLifeHours, String newInstructions) throws ValidationException {
        if (newPrepMinutes < 0 || newBakeMinutes < 0) {
            throw new ValidationException("Preparation and baking minutes cannot be negative.");
        }
        if (newShelfLifeHours != null && (newShelfLifeHours < 1 || newShelfLifeHours > 8760)) {
            throw new ValidationException("Recipe shelf life must be 1-8760 hours.");
        }
        this.items = new ArrayList<>(newItems);
        this.prepMinutes = newPrepMinutes;
        this.bakeMinutes = newBakeMinutes;
        this.shelfLifeHours = newShelfLifeHours;
        this.instructions = newInstructions == null ? "" : newInstructions.trim();
    }

    /**
     * Checks that the recipe can be used for production. Uses a {@link Map} from ingredient id to
     * ingredient for fast lookups.
     */
    public void validateForProduction(Map<Long, Ingredient> ingredients) throws InvalidRecipeException {
        if (items.isEmpty()) {
            throw new InvalidRecipeException("Recipe \"" + name + "\" has no ingredients.");
        }
        if (!Quantities.isPositive(yieldQuantity)) {
            throw new InvalidRecipeException("Recipe \"" + name + "\" must yield more than zero.");
        }
        for (RecipeItem item : items) {
            Ingredient ingredient = ingredients.get(item.getIngredientId());
            if (ingredient == null) {
                throw new InvalidRecipeException("Recipe \"" + name + "\" uses an ingredient that does not exist.");
            }
            if (!ingredient.isActive()) {
                throw new InvalidRecipeException("Ingredient \"" + ingredient.getName() + "\" is inactive.");
            }
            if (!item.getUnit().isCompatibleWith(ingredient.getBaseUnit())) {
                throw new InvalidRecipeException("Unit " + item.getUnit() + " does not fit ingredient \""
                        + ingredient.getName() + "\" (stocked in " + ingredient.getBaseUnit() + ").");
            }
        }
    }

    public void activate(Map<Long, Ingredient> ingredients) throws InvalidRecipeException {
        if (status == RecipeStatus.ARCHIVED) {
            throw new InvalidRecipeException("Archived recipes cannot be activated.");
        }
        validateForProduction(ingredients);
        this.status = RecipeStatus.ACTIVE;
        this.template = false; // reviewed and accepted by the owner
    }

    public void archive() {
        this.status = RecipeStatus.ARCHIVED;
    }

    public void markAsTemplate() {
        this.template = true;
    }

    /**
     * Ingredient requirements for a target number of pieces, in each ingredient's base unit.
     * Example: yield 12 buns with 500 g flour, target 48 -> factor 4 -> 2000 g flour.
     *
     * @return ingredient id -> required quantity (insertion order = recipe order)
     */
    public Map<Long, BigDecimal> scaleTo(BigDecimal targetQuantity, Map<Long, Ingredient> ingredients) {
        BigDecimal factor = targetQuantity.divide(yieldQuantity, 10, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> required = new LinkedHashMap<>();
        for (RecipeItem item : items) {
            Ingredient ingredient = ingredients.get(item.getIngredientId());
            if (ingredient == null) {
                continue;
            }
            BigDecimal perBatch = item.quantityIn(ingredient.getBaseUnit());
            BigDecimal scaled = Quantities.normalize(perBatch.multiply(factor));
            // the same ingredient may appear twice (e.g. butter in dough and in filling)
            BigDecimal previous = required.getOrDefault(item.getIngredientId(), Quantities.ZERO);
            required.put(item.getIngredientId(), previous.add(scaled));
        }
        return required;
    }

    /** Creates an independent DRAFT copy, optionally for another product. */
    public Recipe duplicate(long newId, String newName, long targetProductId, long userId, Instant at)
            throws ValidationException {
        // RecipeItem objects are immutable, so the copy can share them safely
        return new Recipe(newId, newName, targetProductId, yieldQuantity, items, prepMinutes, bakeMinutes,
                shelfLifeHours, instructions, at, userId);
    }

    public String getName() {
        return name;
    }

    public long getProductId() {
        return productId;
    }

    public BigDecimal getYieldQuantity() {
        return yieldQuantity;
    }

    public List<RecipeItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public String getInstructions() {
        return instructions;
    }

    public int getPrepMinutes() {
        return prepMinutes;
    }

    public int getBakeMinutes() {
        return bakeMinutes;
    }

    public Integer getShelfLifeHours() {
        return shelfLifeHours;
    }

    public RecipeStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == RecipeStatus.ACTIVE;
    }

    public boolean isTemplate() {
        return template;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public String toString() {
        return name + " v" + version;
    }
}
