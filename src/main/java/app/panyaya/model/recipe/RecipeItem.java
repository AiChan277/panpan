package app.panyaya.model.recipe;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.Quantities;
import app.panyaya.model.Unit;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;

/** One ingredient line of a recipe, e.g. "500 g high-protein flour (dough)". Immutable. */
public class RecipeItem {

    private long ingredientId;
    private BigDecimal quantity;
    private Unit unit;
    private String note;

    private RecipeItem() {
    }

    public RecipeItem(long ingredientId, BigDecimal quantity, Unit unit, String note) throws ValidationException {
        if (ingredientId <= 0) {
            throw new ValidationException("Each recipe line needs an ingredient.");
        }
        if (!Quantities.isPositive(quantity)) {
            throw new ValidationException("Recipe quantities must be greater than zero.");
        }
        if (unit == null) {
            throw new ValidationException("Each recipe line needs a unit.");
        }
        this.ingredientId = ingredientId;
        this.quantity = Quantities.normalize(quantity);
        this.unit = unit;
        this.note = TextUtil.normalize(note);
    }

    /** Quantity converted to the ingredient's base unit (e.g. 1.5 kg -> 1500 g). */
    public BigDecimal quantityIn(Unit baseUnit) {
        return unit.convert(quantity, baseUnit);
    }

    public long getIngredientId() {
        return ingredientId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public String getNote() {
        return note;
    }
}
