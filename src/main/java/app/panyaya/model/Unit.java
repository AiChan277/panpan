package app.panyaya.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Measurement units. Each unit belongs to a {@link Dimension} and has a factor to that
 * dimension's base unit (g, mL, pcs). Stock is always stored in the base unit.
 *
 * <p>An enum is a class with a fixed set of objects; each constant here carries its own fields.</p>
 */
public enum Unit {
    G("g", Dimension.MASS, "1"),
    KG("kg", Dimension.MASS, "1000"),
    ML("mL", Dimension.VOLUME, "1"),
    L("L", Dimension.VOLUME, "1000"),
    PCS("pcs", Dimension.COUNT, "1"),
    DOZEN("dozen", Dimension.COUNT, "12");

    private final String symbol;
    private final Dimension dimension;
    private final BigDecimal factorToBase;

    Unit(String symbol, Dimension dimension, String factorToBase) {
        this.symbol = symbol;
        this.dimension = dimension;
        this.factorToBase = new BigDecimal(factorToBase);
    }

    public String getSymbol() {
        return symbol;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public boolean isBaseUnit() {
        return factorToBase.compareTo(BigDecimal.ONE) == 0;
    }

    /** The base unit of this unit's dimension (KG -> G, L -> ML, DOZEN -> PCS). */
    public Unit getBaseUnit() {
        switch (dimension) {
            case MASS:
                return G;
            case VOLUME:
                return ML;
            default:
                return PCS;
        }
    }

    public boolean isCompatibleWith(Unit other) {
        return other != null && dimension == other.dimension;
    }

    /**
     * Converts a quantity expressed in this unit into the target unit.
     *
     * @throws IllegalArgumentException when the dimensions differ (e.g. mL to g)
     */
    public BigDecimal convert(BigDecimal quantity, Unit target) {
        if (!isCompatibleWith(target)) {
            throw new IllegalArgumentException("Cannot convert " + symbol + " to " + target.symbol
                    + " (different dimension)");
        }
        BigDecimal inBase = quantity.multiply(factorToBase);
        return inBase.divide(target.factorToBase, Quantities.SCALE, RoundingMode.HALF_UP);
    }

    /** Converts a quantity in this unit into the dimension's base unit. */
    public BigDecimal toBase(BigDecimal quantity) {
        return convert(quantity, getBaseUnit());
    }

    /** All units with the same dimension, used to filter unit pickers in the UI. */
    public static List<Unit> unitsOf(Dimension dimension) {
        List<Unit> result = new ArrayList<>();
        for (Unit unit : values()) {
            if (unit.dimension == dimension) {
                result.add(unit);
            }
        }
        return result;
    }

    /** Only base units may be chosen as an ingredient's stock unit. */
    public static List<Unit> baseUnits() {
        List<Unit> result = new ArrayList<>();
        for (Unit unit : values()) {
            if (unit.isBaseUnit()) {
                result.add(unit);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return symbol;
    }
}
