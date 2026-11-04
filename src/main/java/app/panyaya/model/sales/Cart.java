package app.panyaya.model.sales;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.Money;
import app.panyaya.model.product.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The POS shopping cart. It lives only in memory until checkout.
 *
 * <p>Course topic <b>Collections</b>: a {@link LinkedHashMap} keyed by product id makes "add the same
 * product again" increase the quantity of the existing line, while keeping the order in which the
 * cashier added the products.</p>
 */
public class Cart {

    public static final int MAX_QUANTITY_PER_LINE = 999;

    private final Map<Long, CartLine> lines = new LinkedHashMap<>();

    public void add(Product product, int quantity) throws ValidationException {
        if (quantity < 1) {
            throw new ValidationException("Quantity must be at least 1.");
        }
        CartLine existing = lines.get(product.getId());
        if (existing == null) {
            checkLimit(quantity);
            lines.put(product.getId(), new CartLine(product.getId(), product.getName(), product.getSku(),
                    product.getSellingPrice(), quantity));
        } else {
            int newQuantity = existing.getQuantity() + quantity;
            checkLimit(newQuantity);
            existing.setQuantity(newQuantity);
        }
    }

    /** Sets a line's quantity; zero removes the line. */
    public void setQuantity(long productId, int quantity) throws ValidationException {
        if (quantity < 0) {
            throw new ValidationException("Quantity cannot be negative.");
        }
        CartLine line = lines.get(productId);
        if (line == null) {
            return;
        }
        if (quantity == 0) {
            lines.remove(productId);
        } else {
            checkLimit(quantity);
            line.setQuantity(quantity);
        }
    }

    private void checkLimit(int quantity) throws ValidationException {
        if (quantity > MAX_QUANTITY_PER_LINE) {
            throw new ValidationException("At most " + MAX_QUANTITY_PER_LINE + " pieces per line.");
        }
    }

    public void remove(long productId) {
        lines.remove(productId);
    }

    public void clear() {
        lines.clear();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int quantityOf(long productId) {
        CartLine line = lines.get(productId);
        return line == null ? 0 : line.getQuantity();
    }

    /** A copy of the lines, so callers cannot modify the cart's internal map. */
    public List<CartLine> getLines() {
        return new ArrayList<>(lines.values());
    }

    public BigDecimal getTotal() {
        BigDecimal total = Money.ZERO;
        for (CartLine line : lines.values()) {
            total = total.add(line.getLineTotal());
        }
        return Money.normalize(total);
    }

    public int getItemCount() {
        int count = 0;
        for (CartLine line : lines.values()) {
            count += line.getQuantity();
        }
        return count;
    }
}
