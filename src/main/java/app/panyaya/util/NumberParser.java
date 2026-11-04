package app.panyaya.util;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;

import java.math.BigDecimal;

/**
 * Parses numbers typed by users. The rules are deliberately strict so that a typing mistake is
 * reported instead of silently becoming a wrong value.
 *
 * <ul>
 *   <li><b>Money</b> (whole rupiah): digits, optionally grouped with '.' or ',' and an optional
 *       "Rp" prefix. "18000", "18.000", "Rp 18,000" all mean eighteen thousand.</li>
 *   <li><b>Quantity</b>: digits with at most one decimal separator ('.' or ','), up to 3 decimals,
 *       no grouping. "1.5", "1,5" and "1500" are accepted.</li>
 * </ul>
 */
public final class NumberParser {

    private NumberParser() {
    }

    public static BigDecimal parseMoney(String input, String fieldName) throws ValidationException {
        if (TextUtil.isBlank(input)) {
            throw new ValidationException(fieldName + " is required.");
        }
        String text = input.trim();
        if (text.regionMatches(true, 0, "Rp", 0, 2)) {
            text = text.substring(2).trim();
        }
        // grouping must be in blocks of exactly three digits: 1.000 or 1,000,000
        if (!text.matches("\\d{1,3}([.,]\\d{3})*") && !text.matches("\\d+")) {
            throw new ValidationException(fieldName + " must be a whole rupiah amount, e.g. 18000 or 18.000 (got \""
                    + input.trim() + "\").");
        }
        String digits = text.replace(".", "").replace(",", "");
        return Money.normalize(new BigDecimal(digits));
    }

    public static BigDecimal parseQuantity(String input, String fieldName) throws ValidationException {
        if (TextUtil.isBlank(input)) {
            throw new ValidationException(fieldName + " is required.");
        }
        String text = input.trim().replace(',', '.');
        if (!text.matches("\\d+(\\.\\d{1,3})?")) {
            throw new ValidationException(fieldName + " must be a number with up to 3 decimals, e.g. 1.5 (got \""
                    + input.trim() + "\").");
        }
        return Quantities.normalize(new BigDecimal(text));
    }

    /** Parses a quantity that must be a positive whole number (pieces, production quantity). */
    public static int parseWholeNumber(String input, String fieldName) throws ValidationException {
        if (TextUtil.isBlank(input)) {
            throw new ValidationException(fieldName + " is required.");
        }
        String text = input.trim();
        if (!text.matches("\\d{1,7}")) {
            throw new ValidationException(fieldName + " must be a whole number (got \"" + text + "\").");
        }
        return Integer.parseInt(text);
    }

    /** Optional whole number: blank returns the default value. */
    public static int parseWholeNumber(String input, String fieldName, int defaultValue) throws ValidationException {
        if (TextUtil.isBlank(input)) {
            return defaultValue;
        }
        return parseWholeNumber(input, fieldName);
    }
}
