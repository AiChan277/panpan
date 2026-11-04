package app.panyaya.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * String helpers used across the application.
 *
 * <p>Course topic <b>String</b>: trimming, case conversion, regular expressions, searching,
 * {@link StringBuilder} and padding for receipts all live here so they are easy to find.</p>
 */
public final class TextUtil {

    /** SKU rule: 2-20 characters, only upper-case letters, digits and hyphen. */
    public static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9-]{2,20}");

    /** Ingredient code rule: same shape as a SKU. */
    public static final Pattern CODE_PATTERN = SKU_PATTERN;

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private TextUtil() {
    }

    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** Trims and collapses repeated whitespace: "  Roti   Tawar " becomes "Roti Tawar". */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return MULTIPLE_SPACES.matcher(text.trim()).replaceAll(" ");
    }

    /** Normalizes a SKU or code: trim, upper-case, spaces and underscores become hyphens. */
    public static String normalizeCode(String code) {
        String value = normalize(code).toUpperCase(Locale.ROOT);
        value = value.replace(' ', '-').replace('_', '-');
        while (value.contains("--")) {
            value = value.replace("--", "-");
        }
        return value;
    }

    public static boolean isValidSku(String sku) {
        return sku != null && SKU_PATTERN.matcher(sku).matches();
    }

    /** Case-insensitive "contains" used by all search boxes. An empty query matches everything. */
    public static boolean containsIgnoreCase(String text, String query) {
        if (isBlank(query)) {
            return true;
        }
        if (text == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    /** Case-insensitive equality after normalization, used for uniqueness checks. */
    public static boolean sameKey(String a, String b) {
        return normalize(a).equalsIgnoreCase(normalize(b));
    }

    /**
     * Suggests a SKU from the category and product name, e.g. category "Bread" and name
     * "Japanese Milk Bread" gives "BRE-JMB".
     */
    public static String suggestSku(String categoryName, String productName) {
        StringBuilder sku = new StringBuilder();
        String category = normalizeCode(categoryName).replace("-", "");
        if (!category.isEmpty()) {
            sku.append(category, 0, Math.min(3, category.length())).append('-');
        }
        String[] words = normalize(productName).toUpperCase(Locale.ROOT).split(" ");
        for (String word : words) {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0))) {
                sku.append(word.charAt(0));
            }
        }
        if (words.length == 1 && words[0].length() > 1) {
            // single word: use up to the first 4 letters instead of one initial
            sku.setLength(sku.length() - 1);
            String word = words[0].replaceAll("[^A-Z0-9]", "");
            sku.append(word, 0, Math.min(4, word.length()));
        }
        String result = sku.toString();
        if (result.endsWith("-")) {
            result = result + "ITEM";
        }
        return result.length() > 20 ? result.substring(0, 20) : result;
    }

    public static String padRight(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    public static String padLeft(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() >= width) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        while (builder.length() + value.length() < width) {
            builder.append(' ');
        }
        return builder.append(value).toString();
    }

    public static String center(String text, int width) {
        String value = text == null ? "" : text.trim();
        if (value.length() >= width) {
            return value;
        }
        int left = (width - value.length()) / 2;
        return padRight(padLeft(value, left + value.length()), width);
    }

    public static String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }

    /** Returns the text or a dash when it is empty, for table cells. */
    public static String orDash(String text) {
        return isBlank(text) ? "-" : text;
    }
}
