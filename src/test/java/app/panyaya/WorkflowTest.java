package app.panyaya;

import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.product.Product;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.sales.Cart;
import app.panyaya.model.sales.PaymentInfo;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.report.DateRange;
import app.panyaya.report.Report;
import app.panyaya.report.ReportGenerator;
import app.panyaya.service.DashboardSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static app.panyaya.TestBakery.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WT-01: the complete operational lifecycle, checked against numbers computed by hand.
 *
 * <pre>
 * Japanese Milk Bread, yield 1 loaf: 300 g flour (Rp 12/g) + 40 g butter (Rp 100/g) + 20 g sugar (Rp 15/g)
 *   cost per loaf = 3600 + 4000 + 300 = Rp 7,900
 * Plan 10 -> start -> complete 9 good + 1 rejected
 *   material cost 79,000; unit cost 79,000 / 10 = 7,900; reject waste 7,900
 * Sell 3 (cash 100,000) + 2 (QRIS) at Rp 25,000 -> revenue 125,000, COGS 5 x 7,900 = 39,500
 * Void the QRIS sale -> revenue 75,000, COGS 23,700, gross profit 51,300
 * End of day (next morning): 1 damaged loaf written off (7,900), rest of batch still sellable
 * </pre>
 */
class WorkflowTest {

    @TempDir
    Path dir;

    @Test
    void ingredientsToSalesToReports() throws Exception {
        TestBakery bakery = new TestBakery(dir);

        // 1-3. owner creates ingredients, product and recipe
        Ingredient flour = bakery.ingredient("FLOUR", "Bread flour", Unit.G, "5000", true);
        Ingredient butter = bakery.ingredient("BUTTER", "Butter", Unit.G, "500", true);
        Ingredient sugar = bakery.ingredient("SUGAR", "Sugar", Unit.G, "500", false);
        Product milkBread = bakery.manufactured("Japanese Milk Bread", "BRE-JMB", 25000, 48);
        Recipe recipe = bakery.activeRecipe("Japanese Milk Bread", milkBread, "1", Arrays.asList(
                item(flour, "300", Unit.G), item(butter, "40", Unit.G), item(sugar, "20", Unit.G)));

        // 4. ingredients received into inventory
        bakery.receive(flour, "10", Unit.KG, 120000, bakery.today().plusDays(60));
        bakery.receive(butter, "1", Unit.KG, 100000, bakery.today().plusDays(14));
        bakery.receive(sugar, "1", Unit.KG, 15000, null);
        assertTrue(bakery.ctx.recipes().estimateCost(recipe).isComplete());
        assertEquals(0, Money.of(7900).compareTo(bakery.ctx.recipes().estimateCost(recipe).getCostPerUnit()));

        // 5-9. baker plans, starts and completes production
        ProductionOrder order = bakery.ctx.production().plan(milkBread.getId(), recipe.getId(), 10, bakery.today());
        bakery.ctx.production().start(order.getId());
        bakery.clock.advance(Duration.ofHours(3));
        bakery.ctx.production().complete(order.getId(), 9, 1);
        assertEquals(0, Quantities.of(9).compareTo(bakery.ctx.inventory().sellableQuantity(milkBread.getId())));
        assertEquals(0, Quantities.of(7000).compareTo(bakery.ctx.inventory().getIngredientLots(flour.getId(), false)
                .get(0).getRemainingQuantity()), "10 kg - 10 x 300 g");

        // 10-11. cashier sells; stock decreases
        Sale cashSale = sell(bakery, milkBread, 3, new PaymentInfo(PaymentMethod.CASH, Money.of(100000), ""));
        Sale qrisSale = sell(bakery, milkBread, 2, new PaymentInfo(PaymentMethod.QRIS, null, ""));
        assertEquals(0, Money.of(25000).compareTo(cashSale.getChangeGiven()));
        assertEquals(0, Quantities.of(4).compareTo(bakery.ctx.inventory().sellableQuantity(milkBread.getId())));
        bakery.ctx.sales().voidSale(qrisSale.getId(), "Customer cancelled");
        assertEquals(0, Quantities.of(6).compareTo(bakery.ctx.inventory().sellableQuantity(milkBread.getId())));

        // 12. waste recorded
        FinishedGoodsBatch batch = bakery.ctx.inventory().getBatches(milkBread.getId(), false).get(0);
        bakery.ctx.waste().recordProductWaste(batch.getId(), Quantities.of(1), WasteReason.DAMAGED, "");

        // restart the application: everything must come back from the JSON file
        TestBakery restarted = bakery.reopen();
        assertEquals(0, Quantities.of(5).compareTo(restarted.ctx.inventory().sellableQuantity(milkBread.getId())));

        // 13. dashboard and reports reflect the changes
        DashboardSummary dashboard = restarted.ctx.dashboard().build();
        assertEquals(1, dashboard.getTransactionsToday(), "voided sale excluded");
        assertEquals(0, Money.of(75000).compareTo(dashboard.getRevenueToday()));
        assertEquals(0, Money.of(51300).compareTo(dashboard.getGrossProfitToday()));
        assertEquals(0, Money.of(15800).compareTo(dashboard.getWasteCostToday()), "1 rejected + 1 damaged");
        assertEquals(9, dashboard.getPiecesProducedToday());
        assertEquals(1, dashboard.getProductionVsSales().size());
        assertEquals(3, dashboard.getProductionVsSales().get(0).getSold());

        DateRange today = DateRange.today(restarted.today());
        Report daily = report(restarted, "Daily sales", today);
        assertEquals(Arrays.asList(daily.getRows().get(0).get(1), daily.getRows().get(0).get(3),
                daily.getRows().get(0).get(4), daily.getRows().get(0).get(5), daily.getRows().get(0).get(6)),
                Arrays.asList("1", "Rp 75.000", "Rp 23.700", "Rp 51.300", "1"));

        Report waste = report(restarted, "Waste", today);
        assertEquals(2, waste.getRows().size());
        assertTrue(waste.getSummary().contains("Total estimated loss: Rp 15.800 (2 record(s))"));

        Report production = report(restarted, "Production", today);
        assertEquals("9", production.getRows().get(0).get(5));
        assertEquals("1", production.getRows().get(0).get(6));

        Report usage = report(restarted, "Ingredient usage", today);
        List<String> flourRow = usage.getRows().get(0);
        assertEquals(Arrays.asList("Bread flour", "g", "10000", "3000", "Rp 36.000", "0", "0"), flourRow);

        Report products = report(restarted, "Product performance", today);
        assertEquals("3", products.getRows().get(0).get(2));
        assertEquals("Rp 75.000", products.getRows().get(0).get(3));
        assertEquals("2", products.getRows().get(0).get(7), "waste qty: 1 rejected + 1 damaged");

        // every report runs without error on the same data
        for (ReportGenerator generator : restarted.ctx.reports().getGenerators()) {
            restarted.ctx.reports().generate(generator, DateRange.thisMonth(restarted.today()));
        }
        assertEquals(0, restarted.ctx.getStore().getValidator().validate(restarted.ctx.getStore().getData()).size(),
                "full integrity check passes");
        assertEquals(StockKind.PRODUCT, restarted.ctx.waste().search(null, null, null, null).get(0).getStockKind());
    }

    private static Sale sell(TestBakery bakery, Product product, int quantity, PaymentInfo payment) throws Exception {
        Cart cart = new Cart();
        cart.add(bakery.ctx.products().getProduct(product.getId()), quantity);
        return bakery.ctx.sales().checkout(cart, payment);
    }

    private static Report report(TestBakery bakery, String title, DateRange range) throws Exception {
        for (ReportGenerator generator : bakery.ctx.reports().getGenerators()) {
            if (generator.getTitle().equals(title)) {
                return bakery.ctx.reports().generate(generator, range);
            }
        }
        throw new IllegalArgumentException(title);
    }
}
