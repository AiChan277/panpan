package app.panyaya.util;

import app.panyaya.model.Unit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Display formatting. Money uses the Indonesian convention ("Rp 18.000"); the symbols are set
 * explicitly so the result does not depend on the Windows region settings.
 */
public final class Formatters {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final DecimalFormatSymbols ID_SYMBOLS = createIndonesianSymbols();

    private Formatters() {
    }

    private static DecimalFormatSymbols createIndonesianSymbols() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return symbols;
    }

    /** "Rp 18.000" (rounded half-up to whole rupiah). */
    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        DecimalFormat format = new DecimalFormat("#,##0", ID_SYMBOLS);
        BigDecimal rounded = amount.setScale(0, RoundingMode.HALF_UP);
        String text = format.format(rounded.abs());
        return (rounded.signum() < 0 ? "-Rp " : "Rp ") + text;
    }

    /** Money with two decimals, for small unit costs: "Rp 12,50". */
    public static String unitCost(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", ID_SYMBOLS);
        return "Rp " + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** Plain amount without "Rp", used in CSV files: "18000". */
    public static String plainMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** Quantity without unnecessary zeros: 1500.000 -> "1500", 1.250 -> "1.25". */
    public static String quantity(BigDecimal quantity) {
        if (quantity == null) {
            return "-";
        }
        BigDecimal stripped = quantity.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    public static String quantity(BigDecimal quantity, Unit unit) {
        return quantity(quantity) + " " + (unit == null ? "" : unit.getSymbol());
    }

    public static String percent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString() + " %";
    }

    public static String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    public static String dateTime(Instant instant, ZoneId zone) {
        return instant == null ? "-" : DATE_TIME.format(instant.atZone(zone));
    }

    public static String time(Instant instant, ZoneId zone) {
        return instant == null ? "-" : TIME.format(instant.atZone(zone));
    }
}
