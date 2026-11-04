package app.panyaya.report;

import app.panyaya.model.Money;
import app.panyaya.model.sales.Sale;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Accumulates the totals of several sales (used by the sales reports and the dashboard). */
public class SalesTotals {

    private int transactions;
    private int items;
    private int voided;
    private BigDecimal revenue = Money.ZERO;
    private BigDecimal cost = Money.ZERO;

    /** Completed sales count; voided sales are only counted separately. */
    public void add(Sale sale) {
        if (!sale.isCompleted()) {
            voided++;
            return;
        }
        transactions++;
        items += sale.getItemCount();
        revenue = revenue.add(sale.getTotal());
        cost = cost.add(sale.getCostTotal());
    }

    public int getTransactions() {
        return transactions;
    }

    public int getItems() {
        return items;
    }

    public int getVoided() {
        return voided;
    }

    public BigDecimal getRevenue() {
        return Money.normalize(revenue);
    }

    public BigDecimal getCost() {
        return Money.normalize(cost);
    }

    public BigDecimal getGrossProfit() {
        return Money.normalize(revenue.subtract(cost));
    }

    public BigDecimal getAverageTransaction() {
        if (transactions == 0) {
            return Money.ZERO;
        }
        return revenue.divide(BigDecimal.valueOf(transactions), Money.SCALE, RoundingMode.HALF_UP);
    }

    /** Gross margin in percent, or null when there is no revenue. */
    public BigDecimal getMarginPercent() {
        if (revenue.signum() == 0) {
            return null;
        }
        return getGrossProfit().multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
    }
}
