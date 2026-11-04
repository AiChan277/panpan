package app.panyaya.io;

import app.panyaya.model.BakerySettings;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleLine;
import app.panyaya.model.sales.SaleStatus;
import app.panyaya.util.Formatters;
import app.panyaya.util.TextUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;

/**
 * Builds the plain-text receipt (40 characters wide, like a thermal printer) and saves it as a
 * text file. Course topic <b>String</b>: padding, centering and {@link StringBuilder}.
 */
public final class ReceiptFormatter {

    public static final int WIDTH = 40;

    private ReceiptFormatter() {
    }

    public static String format(Sale sale, BakerySettings settings) {
        ZoneId zone = settings.zone();
        String rule = TextUtil.repeat('-', WIDTH);
        StringBuilder text = new StringBuilder();

        text.append(TextUtil.center(settings.getBakeryName(), WIDTH)).append('\n');
        if (!TextUtil.isBlank(settings.getAddress())) {
            text.append(TextUtil.center(settings.getAddress(), WIDTH)).append('\n');
        }
        if (!TextUtil.isBlank(settings.getPhone())) {
            text.append(TextUtil.center("Tel. " + settings.getPhone(), WIDTH)).append('\n');
        }
        text.append(rule).append('\n');
        text.append(twoColumns("No.", sale.getSaleNo())).append('\n');
        text.append(twoColumns("Date", Formatters.dateTime(sale.getOccurredAt(), zone))).append('\n');
        text.append(twoColumns("Cashier", sale.getCashierName())).append('\n');
        text.append(rule).append('\n');

        for (SaleLine line : sale.getLines()) {
            text.append(TextUtil.padRight(line.getNameSnapshot(), WIDTH)).append('\n');
            String detail = "  " + line.getQuantity() + " x " + Formatters.money(line.getUnitPrice());
            text.append(twoColumns(detail, Formatters.money(line.getLineTotal()))).append('\n');
        }
        text.append(rule).append('\n');
        text.append(twoColumns("TOTAL", Formatters.money(sale.getTotal()))).append('\n');
        text.append(twoColumns("Payment", String.valueOf(sale.getPaymentMethod()))).append('\n');
        if (sale.getPaymentMethod() == PaymentMethod.CASH) {
            text.append(twoColumns("Cash", Formatters.money(sale.getAmountTendered()))).append('\n');
            text.append(twoColumns("Change", Formatters.money(sale.getChangeGiven()))).append('\n');
        } else if (!TextUtil.isBlank(sale.getPaymentReference())) {
            text.append(twoColumns("Reference", sale.getPaymentReference())).append('\n');
        }
        if (sale.getStatus() == SaleStatus.VOIDED) {
            text.append(rule).append('\n');
            text.append(TextUtil.center("*** VOIDED ***", WIDTH)).append('\n');
            text.append(TextUtil.center(sale.getVoidReason(), WIDTH)).append('\n');
        }
        text.append(rule).append('\n');
        if (!TextUtil.isBlank(settings.getReceiptFooter())) {
            text.append(TextUtil.center(settings.getReceiptFooter(), WIDTH)).append('\n');
        }
        return text.toString();
    }

    /** Left text and right-aligned value on one 40-character line. */
    private static String twoColumns(String left, String right) {
        int space = WIDTH - right.length();
        return TextUtil.padRight(left, Math.max(space, 1)) + right;
    }

    /** Saves the receipt as a UTF-8 text file using a BufferedWriter. */
    public static void save(String receiptText, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (String line : receiptText.split("\n")) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}
