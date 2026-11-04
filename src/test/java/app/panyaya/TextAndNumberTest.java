package app.panyaya;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.Unit;
import app.panyaya.util.Formatters;
import app.panyaya.util.NumberParser;
import app.panyaya.util.TextUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAndNumberTest {

    @Test
    void skuRulesAndSearch() {
        assertEquals("RT-TAWAR", TextUtil.normalizeCode("  rt  tawar "));
        assertTrue(TextUtil.isValidSku("RT-TAWAR"));
        assertFalse(TextUtil.isValidSku("R"));
        assertFalse(TextUtil.isValidSku("RT_TAWAR"));
        assertEquals("BRE-JMB", TextUtil.suggestSku("Bread", "Japanese Milk Bread"));
        assertEquals("PAS-CROI", TextUtil.suggestSku("Pastry", "Croissant"));
        assertTrue(TextUtil.containsIgnoreCase("Roti Tawar", "TAWAR"));
        assertTrue(TextUtil.containsIgnoreCase("Roti Tawar", ""));
        assertEquals("Roti Tawar", TextUtil.normalize("  Roti \t  Tawar "));
    }

    @Test
    void moneyParsingAcceptsIndonesianGroupingButRejectsAmbiguity() throws Exception {
        assertEquals(0, Money.of(18000).compareTo(NumberParser.parseMoney("18000", "Price")));
        assertEquals(0, Money.of(18000).compareTo(NumberParser.parseMoney("18.000", "Price")));
        assertEquals(0, Money.of(1500000).compareTo(NumberParser.parseMoney("Rp 1.500.000", "Price")));
        assertThrows(ValidationException.class, () -> NumberParser.parseMoney("1.5", "Price"));
        assertThrows(ValidationException.class, () -> NumberParser.parseMoney("-100", "Price"));
        assertThrows(ValidationException.class, () -> NumberParser.parseMoney("", "Price"));
    }

    @Test
    void quantityParsingAcceptsBothDecimalSeparators() throws Exception {
        assertEquals(0, Quantities.of("1.5").compareTo(NumberParser.parseQuantity("1,5", "Qty")));
        assertEquals(0, Quantities.of("2.375").compareTo(NumberParser.parseQuantity("2.375", "Qty")));
        assertThrows(ValidationException.class, () -> NumberParser.parseQuantity("1.2345", "Qty"));
        assertThrows(ValidationException.class, () -> NumberParser.parseQuantity("1,000.5", "Qty"));
    }

    @Test
    void moneyArithmeticIsExactAndRoundsHalfUp() {
        assertEquals("0.3000", Money.add(Money.of("0.1"), Money.of("0.2")).toPlainString(), "no 0.30000000000000004");
        assertEquals(0, Money.of("3333.3333").compareTo(Money.divide(Money.of(10000), Quantities.of(3))));
        assertEquals("Rp 18.000", Formatters.money(Money.of(18000)));
        assertEquals("Rp 12.501", Formatters.money(Money.of("12500.5")), "display rounds half-up");
        assertEquals("Rp 12,50", Formatters.unitCost(Money.of("12.5")));
    }

    @Test
    void unitConversionStaysInsideOneDimension() {
        assertEquals(0, Quantities.of(1500).compareTo(Unit.KG.convert(Quantities.of("1.5"), Unit.G)));
        assertEquals(0, Quantities.of(24).compareTo(Unit.DOZEN.toBase(Quantities.of(2))));
        assertThrows(IllegalArgumentException.class, () -> Unit.ML.convert(Quantities.of(1), Unit.G));
        assertEquals("1.25 kg", Formatters.quantity(Quantities.of("1.250"), Unit.KG));
    }
}
