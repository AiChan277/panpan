package app.panyaya.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helper for stock quantities. All quantities are {@link BigDecimal} with 3 decimal places,
 * stored in the item's base unit (grams, millilitres or pieces).
 */
public final class Quantities {

    public static final int SCALE = 3;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private Quantities() {
    }

    public static BigDecimal of(String value) {
        return new BigDecimal(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** True when the value has no fractional part (e.g. 12.000). */
    public static boolean isWholeNumber(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
