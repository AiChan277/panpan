package app.panyaya.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when a sale, production start, waste record or adjustment would need more stock than is
 * available. It can carry several shortage lines so the baker sees every missing ingredient at once.
 */
public class InsufficientStockException extends BusinessException {

    private final List<String> shortages;

    public InsufficientStockException(String message) {
        super(message);
        this.shortages = Collections.singletonList(message);
    }

    public InsufficientStockException(List<String> shortages) {
        super(buildMessage(shortages));
        this.shortages = Collections.unmodifiableList(new ArrayList<>(shortages));
    }

    public List<String> getShortages() {
        return shortages;
    }

    private static String buildMessage(List<String> shortages) {
        StringBuilder text = new StringBuilder("Not enough stock:");
        for (String line : shortages) {
            text.append(System.lineSeparator()).append(" - ").append(line);
        }
        return text.toString();
    }
}
