package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.InvalidRecipeException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.product.Product;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.recipe.RecipeItem;
import app.panyaya.model.recipe.RecipeStatus;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Recipe management: create, edit, activate, duplicate, scale and cost recipes. */
public class RecipeService extends BaseService {

    public RecipeService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    public List<Recipe> search(String text, boolean includeArchived) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : data().getRecipes()) {
            Product product = BakeryData.findById(data().getProducts(), recipe.getProductId());
            boolean matches = TextUtil.containsIgnoreCase(recipe.getName(), text)
                    || (product != null && TextUtil.containsIgnoreCase(product.getName(), text));
            if (matches && (includeArchived || recipe.getStatus() != RecipeStatus.ARCHIVED)) {
                result.add(recipe);
            }
        }
        result.sort(Comparator.comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public Recipe getRecipe(long recipeId) {
        return BakeryData.findById(data().getRecipes(), recipeId);
    }

    /** Active recipes that can be used to produce the given product. */
    public List<Recipe> getActiveRecipesFor(long productId) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : data().getRecipes()) {
            if (recipe.getProductId() == productId && recipe.isActive()) {
                result.add(recipe);
            }
        }
        return result;
    }

    /** New recipes start as DRAFT so they can be checked before the baker uses them. */
    public Recipe create(String name, long productId, BigDecimal yield, List<RecipeItem> items, int prepMinutes,
                         int bakeMinutes, Integer shelfLifeHours, String instructions)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_RECIPES);
        return store.execute("CREATE_RECIPE", data -> {
            requireManufactured(data, productId);
            checkItemsExist(data, items);
            Recipe recipe = new Recipe(data.nextId(EntityTypes.RECIPE), name, productId, yield, items, prepMinutes,
                    bakeMinutes, shelfLifeHours, instructions, now(), userId());
            data.getRecipes().add(recipe);
            audit(data, "CREATE_RECIPE", recipe.getName());
            return recipe;
        });
    }

    /** Saves an edit; an ACTIVE recipe must stay valid for production after the edit. */
    public Recipe update(long recipeId, String name, long productId, BigDecimal yield, List<RecipeItem> items,
                         int prepMinutes, int bakeMinutes, Integer shelfLifeHours, String instructions)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_RECIPES);
        return store.execute("UPDATE_RECIPE", data -> {
            Recipe recipe = BakeryData.require(data.getRecipes(), recipeId, "Recipe");
            requireManufactured(data, productId);
            checkItemsExist(data, items);
            recipe.update(name, productId, yield, items, prepMinutes, bakeMinutes, shelfLifeHours, instructions,
                    userId(), now());
            if (recipe.isActive()) {
                recipe.validateForProduction(BakeryData.indexById(data.getIngredients()));
            }
            audit(data, "UPDATE_RECIPE", recipe.getName() + " v" + recipe.getVersion());
            return recipe;
        });
    }

    /**
     * Activates a recipe. Starter templates contain illustrative quantities only, so the owner must
     * confirm having checked them ({@code templateReviewed}).
     */
    public void activate(long recipeId, boolean templateReviewed) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_PRODUCTS); // only the Owner may release a recipe for production
        store.execute("ACTIVATE_RECIPE", data -> {
            Recipe recipe = BakeryData.require(data.getRecipes(), recipeId, "Recipe");
            if (recipe.isTemplate() && !templateReviewed) {
                throw new InvalidRecipeException("This is a starter template with example quantities. "
                        + "Please confirm that you have checked the quantities.");
            }
            requireManufactured(data, recipe.getProductId());
            recipe.activate(BakeryData.indexById(data.getIngredients()));
            audit(data, "ACTIVATE_RECIPE", recipe.getName());
            return null;
        });
    }

    public void archive(long recipeId) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_RECIPES);
        store.execute("ARCHIVE_RECIPE", data -> {
            Recipe recipe = BakeryData.require(data.getRecipes(), recipeId, "Recipe");
            recipe.archive();
            audit(data, "ARCHIVE_RECIPE", recipe.getName());
            return null;
        });
    }

    /** Copies a recipe as a new DRAFT, optionally for a different product. */
    public Recipe duplicate(long recipeId, String newName, long targetProductId)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_RECIPES);
        return store.execute("DUPLICATE_RECIPE", data -> {
            Recipe original = BakeryData.require(data.getRecipes(), recipeId, "Recipe");
            requireManufactured(data, targetProductId);
            Recipe copy = original.duplicate(data.nextId(EntityTypes.RECIPE), newName, targetProductId, userId(), now());
            data.getRecipes().add(copy);
            audit(data, "DUPLICATE_RECIPE", original.getName() + " -> " + copy.getName());
            return copy;
        });
    }

    /** Scale calculator: what is needed to make {@code targetQuantity} pieces, and what is in stock. */
    public List<MaterialRequirement> calculateRequirements(Recipe recipe, BigDecimal targetQuantity) {
        return calculateRequirements(data(), recipe, targetQuantity, now(), zone());
    }

    public static List<MaterialRequirement> calculateRequirements(BakeryData data, Recipe recipe,
                                                                  BigDecimal targetQuantity, Instant now, ZoneId zone) {
        Map<Long, Ingredient> ingredients = BakeryData.indexById(data.getIngredients());
        Map<Long, BigDecimal> required = recipe.scaleTo(targetQuantity, ingredients);
        List<MaterialRequirement> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : required.entrySet()) {
            Ingredient ingredient = ingredients.get(entry.getKey());
            BigDecimal available = StockQueries.usableQuantity(data, ingredient.getId(), now, zone);
            BigDecimal unitCost = StockQueries.estimatedUnitCost(data, ingredient.getId(), now, zone);
            BigDecimal cost;
            if (!ingredient.isStockTracked()) {
                cost = Money.ZERO; // e.g. tap water
            } else {
                cost = unitCost == null ? null : Money.multiply(unitCost, entry.getValue());
            }
            result.add(new MaterialRequirement(ingredient.getId(), ingredient.getName(), ingredient.getBaseUnit(),
                    entry.getValue(), available, ingredient.isStockTracked(), cost));
        }
        return result;
    }

    /** Estimated material cost per batch and per piece. */
    public CostEstimate estimateCost(Recipe recipe) {
        BigDecimal total = Money.ZERO;
        List<String> unknown = new ArrayList<>();
        for (MaterialRequirement requirement : calculateRequirements(recipe, recipe.getYieldQuantity())) {
            if (requirement.getEstimatedCost() == null) {
                unknown.add(requirement.getIngredientName());
            } else {
                total = total.add(requirement.getEstimatedCost());
            }
        }
        BigDecimal batch = Money.normalize(total);
        BigDecimal perUnit = Quantities.isPositive(recipe.getYieldQuantity())
                ? Money.divide(batch, recipe.getYieldQuantity()) : Money.ZERO;
        return new CostEstimate(batch, perUnit, unknown);
    }

    private static void requireManufactured(BakeryData data, long productId) throws ValidationException {
        Product product = BakeryData.require(data.getProducts(), productId, "Product");
        if (!product.isManufactured()) {
            throw new ValidationException("\"" + product.getName() + "\" is bought for resale and has no recipe.");
        }
    }

    private static void checkItemsExist(BakeryData data, List<RecipeItem> items) throws ValidationException {
        for (RecipeItem item : items) {
            Ingredient ingredient = BakeryData.require(data.getIngredients(), item.getIngredientId(), "Ingredient");
            if (!item.getUnit().isCompatibleWith(ingredient.getBaseUnit())) {
                throw new ValidationException("Unit " + item.getUnit() + " cannot be used for \"" + ingredient.getName()
                        + "\" (stocked in " + ingredient.getBaseUnit() + ").");
            }
        }
    }
}
