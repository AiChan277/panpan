package app.panyaya.service;

import app.panyaya.model.waste.WasteReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the dashboard shows, computed from stored records only. */
public class DashboardSummary {

    /** Production vs sales for one product today. */
    public static class ProductDay {
        private final String productName;
        private final int produced;
        private final int sold;
        private final BigDecimal wasted;
        private final BigDecimal remaining;

        public ProductDay(String productName, int produced, int sold, BigDecimal wasted, BigDecimal remaining) {
            this.productName = productName;
            this.produced = produced;
            this.sold = sold;
            this.wasted = wasted;
            this.remaining = remaining;
        }

        public String getProductName() {
            return productName;
        }

        public int getProduced() {
            return produced;
        }

        public int getSold() {
            return sold;
        }

        public BigDecimal getWasted() {
            return wasted;
        }

        public BigDecimal getRemaining() {
            return remaining;
        }
    }

    /** One line of an alert list, e.g. "Butter - lot L-20261104-0001 expires 06 Nov 2026". */
    public static class Alert {
        private final String title;
        private final String detail;

        public Alert(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }

        public String getTitle() {
            return title;
        }

        public String getDetail() {
            return detail;
        }
    }

    public static class TopProduct {
        private final String name;
        private final int quantity;
        private final BigDecimal revenue;

        public TopProduct(String name, int quantity, BigDecimal revenue) {
            this.name = name;
            this.quantity = quantity;
            this.revenue = revenue;
        }

        public String getName() {
            return name;
        }

        public int getQuantity() {
            return quantity;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }
    }

    LocalDate businessDate;
    BigDecimal revenueToday;
    int transactionsToday;
    BigDecimal averageTransaction;
    BigDecimal grossProfitToday;
    BigDecimal wasteCostToday;
    BigDecimal wasteCost7Days;
    BigDecimal ingredientStockValue;
    BigDecimal productStockValue;
    BigDecimal valueAtRisk;
    int productsAvailable;
    int productsSoldOut;
    int productionCompletedToday;
    int piecesProducedToday;
    final List<Alert> lowStock = new ArrayList<>();
    final List<Alert> expiringIngredients = new ArrayList<>();
    final List<Alert> expiringProducts = new ArrayList<>();
    final List<ProductDay> productionVsSales = new ArrayList<>();
    final List<TopProduct> topProducts = new ArrayList<>();
    final Map<LocalDate, BigDecimal> revenueLast14Days = new LinkedHashMap<>();
    final Map<WasteReason, BigDecimal> wasteByReason7Days = new EnumMap<>(WasteReason.class);

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public BigDecimal getRevenueToday() {
        return revenueToday;
    }

    public int getTransactionsToday() {
        return transactionsToday;
    }

    public BigDecimal getAverageTransaction() {
        return averageTransaction;
    }

    public BigDecimal getGrossProfitToday() {
        return grossProfitToday;
    }

    public BigDecimal getWasteCostToday() {
        return wasteCostToday;
    }

    public BigDecimal getWasteCost7Days() {
        return wasteCost7Days;
    }

    public BigDecimal getIngredientStockValue() {
        return ingredientStockValue;
    }

    public BigDecimal getProductStockValue() {
        return productStockValue;
    }

    public BigDecimal getValueAtRisk() {
        return valueAtRisk;
    }

    public int getProductsAvailable() {
        return productsAvailable;
    }

    public int getProductsSoldOut() {
        return productsSoldOut;
    }

    public int getProductionCompletedToday() {
        return productionCompletedToday;
    }

    public int getPiecesProducedToday() {
        return piecesProducedToday;
    }

    public List<Alert> getLowStock() {
        return lowStock;
    }

    public List<Alert> getExpiringIngredients() {
        return expiringIngredients;
    }

    public List<Alert> getExpiringProducts() {
        return expiringProducts;
    }

    public List<ProductDay> getProductionVsSales() {
        return productionVsSales;
    }

    public List<TopProduct> getTopProducts() {
        return topProducts;
    }

    public Map<LocalDate, BigDecimal> getRevenueLast14Days() {
        return revenueLast14Days;
    }

    public Map<WasteReason, BigDecimal> getWasteByReason7Days() {
        return wasteByReason7Days;
    }
}
