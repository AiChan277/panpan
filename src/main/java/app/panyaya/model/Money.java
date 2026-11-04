package app.panyaya.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helper for monetary values (Indonesian Rupiah).
 *
 * <p>Course topic <b>Java Variables</b>: money is always a {@link BigDecimal} (a reference type),
 * never {@code double}, because binary floating point cannot represent values such as 0.1 exactly.
 * The {@code static final} constants below are shared by the whole application.</p>
 */
public final class Money {

    /** Stored scale: 4 decimal places, enough for unit costs such as Rp 12.5000 per gram. */
    public static final int SCALE = 4;

    /** Rounding rule used everywhere (half-up, as taught in school arithmetic). */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    /** Upper limit for a selling price, protects against typing errors. */
    public static final BigDecimal MAX_PRICE = new BigDecimal("100000000");

    private Money() {
        // utility class: no objects
    }

    public static BigDecimal of(long rupiah) {
        return BigDecimal.valueOf(rupiah).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String amount) {
        return new BigDecimal(amount).setScale(SCALE, ROUNDING);
    }

    /** Returns the value with the storage scale applied (null becomes zero). */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(SCALE, ROUNDING);
    }

    /** price x quantity, rounded to the storage scale. */
    public static BigDecimal multiply(BigDecimal price, BigDecimal quantity) {
        return normalize(price.multiply(quantity));
    }

    /** amount / quantity, rounded to the storage scale. Used for unit costs. */
    public static BigDecimal divide(BigDecimal amount, BigDecimal quantity) {
        if (quantity.signum() == 0) {
            throw new ArithmeticException("Cannot divide money by zero quantity");
        }
        return amount.divide(quantity, SCALE, ROUNDING);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return normalize(normalize(a).add(normalize(b)));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
