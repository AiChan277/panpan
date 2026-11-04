package app.panyaya.service;

import app.panyaya.exception.AuthorizationException;
import app.panyaya.exception.DataCorruptedException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.BakerySettings;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.product.Product;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.production.ProductionStatus;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleLine;
import app.panyaya.model.user.Permission;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.persistence.DataStore;
import app.panyaya.report.SalesTotals;
import app.panyaya.util.Formatters;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the dashboard. Every number comes from stored records; nothing is invented by the UI.
 * Runs on a background copy of the data.
 */
public class DashboardService extends BaseService {

    public DashboardService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    public DashboardSummary build() throws AuthorizationException, DataCorruptedException {
        session.require(Permission.VIEW_DASHBOARD);
        return build(store.readCommittedCopy(), now());
    }

    public static DashboardSummary build(BakeryData data, Instant now) {
        BakerySettings settings = data.getSettings();
        ZoneId zone = settings.zone();
        LocalDate today = LocalDate.ofInstant(now, zone);
        DashboardSummary summary = new DashboardSummary();
        summary.businessDate = today;

        // --- sales today and the last 14 days
        SalesTotals todayTotals = new SalesTotals();
        for (int i = 13; i >= 0; i--) {
            summary.revenueLast14Days.put(today.minusDays(i), Money.ZERO);
        }
        Map<Long, Integer> soldToday = new HashMap<>();
        Map<String, DashboardSummary.TopProduct> top = new LinkedHashMap<>();
        for (Sale sale : data.getSales()) {
            if (!sale.isCompleted()) {
                continue;
            }
            LocalDate day = sale.getBusinessDate();
            if (summary.revenueLast14Days.containsKey(day)) {
                summary.revenueLast14Days.put(day, summary.revenueLast14Days.get(day).add(sale.getTotal()));
            }
            if (day.equals(today)) {
                todayTotals.add(sale);
            }
            boolean last7Days = !day.isBefore(today.minusDays(6)) && !day.isAfter(today);
            for (SaleLine line : sale.getLines()) {
                if (day.equals(today)) {
                    soldToday.merge(line.getProductId(), line.getQuantity(), Integer::sum);
                }
                if (last7Days) {
                    DashboardSummary.TopProduct previous = top.get(line.getNameSnapshot());
                    int quantity = line.getQuantity() + (previous == null ? 0 : previous.getQuantity());
                    BigDecimal revenue = line.getLineTotal().add(previous == null ? Money.ZERO : previous.getRevenue());
                    top.put(line.getNameSnapshot(), new DashboardSummary.TopProduct(line.getNameSnapshot(), quantity, revenue));
                }
            }
        }
        summary.revenueToday = todayTotals.getRevenue();
        summary.transactionsToday = todayTotals.getTransactions();
        summary.averageTransaction = todayTotals.getAverageTransaction();
        summary.grossProfitToday = todayTotals.getGrossProfit();
        List<DashboardSummary.TopProduct> topList = new ArrayList<>(top.values());
        topList.sort((a, b) -> Integer.compare(b.getQuantity(), a.getQuantity()));
        summary.topProducts.addAll(topList.subList(0, Math.min(5, topList.size())));

        // --- waste
        BigDecimal wasteToday = Money.ZERO;
        BigDecimal waste7 = Money.ZERO;
        Map<Long, BigDecimal> wastedToday = new HashMap<>();
        for (WasteRecord waste : data.getWasteRecords()) {
            LocalDate day = waste.getBusinessDate();
            if (day.equals(today)) {
                wasteToday = wasteToday.add(waste.getEstimatedLoss());
                if (waste.getStockKind() == StockKind.PRODUCT) {
                    wastedToday.merge(waste.getItemId(), waste.getQuantity(), BigDecimal::add);
                }
            }
            if (!day.isBefore(today.minusDays(6)) && !day.isAfter(today)) {
                waste7 = waste7.add(waste.getEstimatedLoss());
                summary.wasteByReason7Days.merge(waste.getReason(), waste.getEstimatedLoss(), BigDecimal::add);
            }
        }
        summary.wasteCostToday = Money.normalize(wasteToday);
        summary.wasteCost7Days = Money.normalize(waste7);

        // --- ingredients: low stock, expiring lots, value
        BigDecimal ingredientValue = Money.ZERO;
        BigDecimal risk = Money.ZERO;
        for (Ingredient ingredient : data.getIngredients()) {
            if (!ingredient.isActive()) {
                continue;
            }
            IngredientStock stock = IngredientService.stockOf(data, ingredient, now, zone);
            ingredientValue = ingredientValue.add(stock.getValue());
            if (stock.needsAttention()) {
                summary.lowStock.add(new DashboardSummary.Alert(ingredient.getName(), stock.getStatus() + ": "
                        + Formatters.quantity(stock.getUsableQuantity(), ingredient.getBaseUnit()) + " (minimum "
                        + Formatters.quantity(ingredient.getMinimumStock(), ingredient.getBaseUnit()) + ")"));
            }
        }
        for (IngredientLot lot : data.getIngredientLots()) {
            if (lot.isDepleted()) {
                continue;
            }
            Ingredient ingredient = BakeryData.findById(data.getIngredients(), lot.getItemId());
            String name = ingredient == null ? "?" : ingredient.getName();
            if (lot.isExpiredAt(now, zone)) {
                risk = risk.add(lot.getRemainingValue());
                summary.expiringIngredients.add(new DashboardSummary.Alert("EXPIRED " + name,
                        "Lot " + lot.getLotCode() + " expired " + lot.describeExpiry(zone) + " - write it off"));
            } else if (lot.expiresWithinDays(now, zone, settings.getNearExpiryDays())) {
                summary.expiringIngredients.add(new DashboardSummary.Alert(name,
                        "Lot " + lot.getLotCode() + " expires " + lot.describeExpiry(zone)));
            }
        }

        // --- products: availability, expiring batches, production vs sales
        BigDecimal productValue = Money.ZERO;
        Map<Long, Integer> producedToday = new HashMap<>();
        for (ProductionOrder order : data.getProductionOrders()) {
            if (order.getStatus() == ProductionStatus.COMPLETED && order.getFinishedAt() != null
                    && LocalDate.ofInstant(order.getFinishedAt(), zone).equals(today)) {
                summary.productionCompletedToday++;
                summary.piecesProducedToday += order.getGoodQuantity();
                producedToday.merge(order.getProductId(), order.getGoodQuantity(), Integer::sum);
            }
        }
        for (Product product : data.getProducts()) {
            BigDecimal sellable = Quantities.ZERO;
            for (FinishedGoodsBatch batch : StockQueries.batchesOf(data, product.getId())) {
                if (batch.isDepleted()) {
                    continue;
                }
                if (batch.isExpiredAt(now, zone)) {
                    risk = risk.add(batch.getRemainingValue());
                    summary.expiringProducts.add(new DashboardSummary.Alert("EXPIRED " + product.getName(),
                            Formatters.quantity(batch.getRemainingQuantity()) + " pcs in " + batch.getLotCode()
                                    + " - write off"));
                } else {
                    sellable = sellable.add(batch.getRemainingQuantity());
                    productValue = productValue.add(batch.getRemainingValue());
                    if (batch.expiresWithinHours(now, settings.getNearExpiryHours())) {
                        summary.expiringProducts.add(new DashboardSummary.Alert(product.getName(),
                                Formatters.quantity(batch.getRemainingQuantity()) + " pcs expire "
                                        + Formatters.dateTime(batch.getExpiresAt(), zone)));
                    }
                }
            }
            if (product.isActive()) {
                if (sellable.signum() > 0) {
                    summary.productsAvailable++;
                } else {
                    summary.productsSoldOut++;
                }
            }
            int produced = producedToday.getOrDefault(product.getId(), 0);
            int sold = soldToday.getOrDefault(product.getId(), 0);
            BigDecimal wasted = wastedToday.getOrDefault(product.getId(), Quantities.ZERO);
            if (produced > 0 || sold > 0 || wasted.signum() > 0) {
                summary.productionVsSales.add(new DashboardSummary.ProductDay(product.getName(), produced, sold,
                        wasted, sellable));
            }
        }
        summary.ingredientStockValue = Money.normalize(ingredientValue);
        summary.productStockValue = Money.normalize(productValue);
        summary.valueAtRisk = Money.normalize(risk);
        return summary;
    }
}
