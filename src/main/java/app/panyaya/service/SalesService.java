package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.InsufficientStockException;
import app.panyaya.exception.InvalidTransactionException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.io.ReceiptFormatter;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Quantities;
import app.panyaya.model.inventory.Allocation;
import app.panyaya.model.inventory.FefoAllocator;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.SaleIssue;
import app.panyaya.model.inventory.SaleReversal;
import app.panyaya.model.product.Product;
import app.panyaya.model.sales.Cart;
import app.panyaya.model.sales.CartLine;
import app.panyaya.model.sales.PaymentInfo;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleAllocation;
import app.panyaya.model.sales.SaleLine;
import app.panyaya.model.sales.SaleStatus;
import app.panyaya.model.user.Permission;
import app.panyaya.model.user.User;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.Formatters;
import app.panyaya.util.TextUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Point of sale: sellable products, checkout, void and sales history. */
public class SalesService extends BaseService {

    private final FefoAllocator<FinishedGoodsBatch> allocator = new FefoAllocator<>();

    public SalesService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    /** Active products with their sellable quantity, filtered by name or SKU. */
    public List<ProductAvailability> getSellableProducts(String search) {
        List<ProductAvailability> result = new ArrayList<>();
        for (Product product : data().getProducts()) {
            if (!product.isActive()) {
                continue;
            }
            if (!TextUtil.containsIgnoreCase(product.getName(), search)
                    && !TextUtil.containsIgnoreCase(product.getSku(), search)) {
                continue;
            }
            result.add(new ProductAvailability(product,
                    StockQueries.sellableQuantity(data(), product.getId(), now(), zone())));
        }
        result.sort(Comparator.comparing((ProductAvailability p) -> p.getProduct().getName(),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Exact SKU match first (barcode scanners type the SKU), otherwise null. */
    public Product findBySku(String sku) {
        String clean = TextUtil.normalizeCode(sku);
        for (Product product : data().getProducts()) {
            if (product.isActive() && product.getSku().equals(clean)) {
                return product;
            }
        }
        return null;
    }

    /**
     * Completes a sale. Stock is checked again here on the newest data (not only when items were
     * added to the cart). The sale, its lines, FEFO allocations, payment and stock movements are
     * saved together, or nothing is saved and the cart stays as it is.
     */
    public Sale checkout(Cart cart, PaymentInfo payment) throws BusinessException, PersistenceException {
        session.require(Permission.USE_POS);
        if (cart.isEmpty()) {
            throw new InvalidTransactionException("The cart is empty.");
        }
        return store.execute("SALE_CHECKOUT", data -> {
            User cashier = session.getUser();
            LocalDate businessDate = today();

            // 1) all lines must be available before anything changes
            List<String> shortages = new ArrayList<>();
            for (CartLine line : cart.getLines()) {
                Product product = BakeryData.require(data.getProducts(), line.getProductId(), "Product");
                if (!product.isActive()) {
                    throw new ValidationException("\"" + product.getName() + "\" is archived and cannot be sold.");
                }
                BigDecimal sellable = allocator.available(StockQueries.batchesOf(data, product.getId()), now(), zone());
                if (sellable.compareTo(Quantities.of(line.getQuantity())) < 0) {
                    shortages.add(product.getName() + ": only " + Formatters.quantity(sellable)
                            + " sellable, cart has " + line.getQuantity() + " (expired stock is never sold)");
                }
            }
            if (!shortages.isEmpty()) {
                throw new InsufficientStockException(shortages);
            }

            // 2) build the sale and take stock FEFO
            Sale sale = new Sale(data.nextId(EntityTypes.SALE), data.nextNumber("S", businessDate), now(),
                    businessDate, cashier.getId(), cashier.getDisplayName());
            for (CartLine cartLine : cart.getLines()) {
                Product product = BakeryData.findById(data.getProducts(), cartLine.getProductId());
                // price at checkout time is copied onto the line
                SaleLine line = new SaleLine(product.getId(), product.getSku(), product.getName(),
                        cartLine.getQuantity(), product.getSellingPrice());
                List<Allocation<FinishedGoodsBatch>> allocations = allocator.allocate(
                        StockQueries.batchesOf(data, product.getId()), Quantities.of(cartLine.getQuantity()), now(), zone());
                for (Allocation<FinishedGoodsBatch> allocation : allocations) {
                    FinishedGoodsBatch batch = allocation.getLot();
                    long movementId = data.nextId(EntityTypes.MOVEMENT);
                    post(data, new SaleIssue(movementId, now(), businessDate, batch, allocation.getQuantity(),
                            cashier.getId(), sale.getSaleNo(), sale.getId()), batch);
                    line.addAllocation(new SaleAllocation(batch.getId(), batch.getLotCode(), allocation.getQuantity(),
                            batch.getUnitCost(), movementId));
                }
                sale.addLine(line);
            }
            sale.complete(payment);
            data.getSales().add(sale);
            audit(data, "SALE_COMPLETED", sale.getSaleNo() + " " + sale.getTotal().toPlainString());
            return sale;
        });
    }

    /**
     * Voids a completed sale (Owner only, same business day, with a reason). Stock goes back to the
     * batches it came from through compensating movements; the sale itself is kept as VOIDED.
     */
    public Sale voidSale(long saleId, String reason) throws BusinessException, PersistenceException {
        session.require(Permission.VOID_SALE);
        return store.execute("SALE_VOID", data -> {
            Sale sale = BakeryData.require(data.getSales(), saleId, "Sale");
            if (!sale.getBusinessDate().equals(today())) {
                throw new InvalidTransactionException("Only sales from today can be voided (sale " + sale.getSaleNo()
                        + " is from " + Formatters.date(sale.getBusinessDate()) + ").");
            }
            sale.markVoided(reason, userId(), now());
            for (SaleLine line : sale.getLines()) {
                for (SaleAllocation allocation : line.getAllocations()) {
                    FinishedGoodsBatch batch = BakeryData.require(data.getFinishedGoodsBatches(),
                            allocation.getBatchId(), "Batch");
                    post(data, new SaleReversal(data.nextId(EntityTypes.MOVEMENT), now(), today(), batch,
                            allocation.getQuantity(), userId(), sale.getSaleNo(), sale.getId(),
                            allocation.getMovementId(), sale.getVoidReason()), batch);
                }
            }
            audit(data, "SALE_VOIDED", sale.getSaleNo() + ": " + sale.getVoidReason());
            return sale;
        });
    }

    /** Sales history. Cashiers only see their own sales; the Owner sees all. */
    public List<Sale> search(LocalDate from, LocalDate to, String text, PaymentMethod method, SaleStatus status) {
        boolean seeAll = session.can(Permission.VIEW_ALL_SALES);
        List<Sale> result = new ArrayList<>();
        for (Sale sale : data().getSales()) {
            if (!seeAll && sale.getCashierId() != session.getUserId()) {
                continue;
            }
            if (from != null && sale.getBusinessDate().isBefore(from)) {
                continue;
            }
            if (to != null && sale.getBusinessDate().isAfter(to)) {
                continue;
            }
            if (method != null && sale.getPaymentMethod() != method) {
                continue;
            }
            if (status != null && sale.getStatus() != status) {
                continue;
            }
            if (!TextUtil.isBlank(text) && !TextUtil.containsIgnoreCase(sale.getSaleNo(), text)
                    && !containsProduct(sale, text)) {
                continue;
            }
            result.add(sale);
        }
        result.sort(Comparator.comparing(Sale::getOccurredAt).reversed());
        return result;
    }

    private static boolean containsProduct(Sale sale, String text) {
        for (SaleLine line : sale.getLines()) {
            if (TextUtil.containsIgnoreCase(line.getNameSnapshot(), text)) {
                return true;
            }
        }
        return false;
    }

    public String receiptText(Sale sale) {
        return ReceiptFormatter.format(sale, data().getSettings());
    }

    public void saveReceipt(Sale sale, Path file) throws IOException {
        ReceiptFormatter.save(receiptText(sale), file);
    }
}
