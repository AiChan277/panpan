package app.panyaya.model.sales;

import app.panyaya.exception.InvalidTransactionException;
import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;
import app.panyaya.util.TextUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A POS transaction. Built in memory with status OPEN, then {@link #complete} validates the payment
 * and locks it as COMPLETED. A completed sale is never deleted; a mistake is corrected with
 * {@link #markVoided}, which keeps the record for the audit trail.
 */
public class Sale extends BaseEntity {

    private String saleNo;
    private Instant occurredAt;
    private LocalDate businessDate;
    private long cashierId;
    private String cashierName;
    private SaleStatus status = SaleStatus.OPEN;
    private List<SaleLine> lines = new ArrayList<>();
    private BigDecimal subtotal = Money.ZERO;
    private BigDecimal total = Money.ZERO;
    private PaymentMethod paymentMethod;
    private BigDecimal amountTendered;
    private BigDecimal changeGiven = Money.ZERO;
    private String paymentReference;
    private String voidReason;
    private Instant voidedAt;
    private long voidedBy;

    private Sale() {
    }

    public Sale(long id, String saleNo, Instant occurredAt, LocalDate businessDate, long cashierId, String cashierName) {
        super(id);
        this.saleNo = saleNo;
        this.occurredAt = occurredAt;
        this.businessDate = businessDate;
        this.cashierId = cashierId;
        this.cashierName = cashierName;
    }

    public void addLine(SaleLine line) {
        if (status != SaleStatus.OPEN) {
            throw new IllegalStateException("Lines can only be added while the sale is open");
        }
        lines.add(line);
        subtotal = Money.add(subtotal, line.getLineTotal());
        total = subtotal; // no discounts or taxes in version 1
    }

    /** Validates lines and payment, then locks the sale. */
    public void complete(PaymentInfo payment) throws InvalidTransactionException {
        if (status != SaleStatus.OPEN) {
            throw new InvalidTransactionException("Sale " + saleNo + " is already " + status + ".");
        }
        if (lines.isEmpty()) {
            throw new InvalidTransactionException("The cart is empty.");
        }
        if (!Money.isPositive(total)) {
            throw new InvalidTransactionException("The total must be greater than zero.");
        }
        payment.validate(total);
        this.paymentMethod = payment.getMethod();
        this.amountTendered = payment.getMethod().requiresTenderedAmount() ? payment.getTendered() : total;
        this.changeGiven = payment.changeFor(total);
        this.paymentReference = payment.getReference();
        this.status = SaleStatus.COMPLETED;
    }

    /** Marks the sale as voided. Stock reversal movements are created by SalesService. */
    public void markVoided(String reason, long userId, Instant at) throws InvalidTransactionException {
        if (status != SaleStatus.COMPLETED) {
            throw new InvalidTransactionException("Only completed sales can be voided (sale is " + status + ").");
        }
        String cleanReason = TextUtil.normalize(reason);
        if (cleanReason.length() < 3) {
            throw new InvalidTransactionException("Please give a reason for the void (at least 3 characters).");
        }
        this.status = SaleStatus.VOIDED;
        this.voidReason = cleanReason;
        this.voidedAt = at;
        this.voidedBy = userId;
    }

    public BigDecimal getCostTotal() {
        BigDecimal cost = Money.ZERO;
        for (SaleLine line : lines) {
            cost = cost.add(line.getCostTotal());
        }
        return Money.normalize(cost);
    }

    /** Gross profit = revenue - cost of goods sold (labour and overhead are not included). */
    public BigDecimal getGrossProfit() {
        return Money.normalize(total.subtract(getCostTotal()));
    }

    public int getItemCount() {
        int count = 0;
        for (SaleLine line : lines) {
            count += line.getQuantity();
        }
        return count;
    }

    public boolean isCompleted() {
        return status == SaleStatus.COMPLETED;
    }

    public String getSaleNo() {
        return saleNo;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public long getCashierId() {
        return cashierId;
    }

    public String getCashierName() {
        return cashierName;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public List<SaleLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getAmountTendered() {
        return amountTendered;
    }

    public BigDecimal getChangeGiven() {
        return changeGiven;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public long getVoidedBy() {
        return voidedBy;
    }
}
