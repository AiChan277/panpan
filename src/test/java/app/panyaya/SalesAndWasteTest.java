package app.panyaya;

import app.panyaya.exception.AuthorizationException;
import app.panyaya.exception.InsufficientStockException;
import app.panyaya.exception.InvalidTransactionException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.SaleReversal;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.sales.Cart;
import app.panyaya.model.sales.PaymentInfo;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.sales.SaleStatus;
import app.panyaya.model.user.Role;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.service.ReceiptLineInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static app.panyaya.TestBakery.item;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesAndWasteTest {

    @TempDir
    Path dir;

    private TestBakery bakery;
    private Product bread;

    /** Bakes 20 loaves of bread (Rp 18,000 each, 48 h shelf life). */
    private void setUp() throws Exception {
        bakery = new TestBakery(dir);
        Ingredient flour = bakery.ingredient("FLOUR", "Flour", Unit.G, "1000", false);
        bakery.receive(flour, "10", Unit.KG, 100000, null); // Rp 10/g
        bread = bakery.manufactured("White Bread", "BRE-WHITE", 18000, 48);
        Recipe recipe = bakery.activeRecipe("White Bread", bread, "1", Collections.singletonList(item(flour, "430", Unit.G)));
        ProductionOrder order = bakery.ctx.production().plan(bread.getId(), recipe.getId(), 20, bakery.today());
        bakery.ctx.production().start(order.getId());
        bakery.ctx.production().complete(order.getId(), 20, 0); // unit cost 4300
    }

    private Sale sell(int quantity, PaymentInfo payment) throws Exception {
        Cart cart = new Cart();
        cart.add(bakery.ctx.products().getProduct(bread.getId()), quantity);
        return bakery.ctx.sales().checkout(cart, payment);
    }

    @Test
    void checkoutComputesTotalsChangeAndDeductsStock() throws Exception {
        setUp();
        Sale sale = sell(3, new PaymentInfo(PaymentMethod.CASH, Money.of(60000), ""));

        assertEquals(SaleStatus.COMPLETED, sale.getStatus());
        assertEquals(0, Money.of(54000).compareTo(sale.getTotal()));
        assertEquals(0, Money.of(6000).compareTo(sale.getChangeGiven()));
        assertEquals(0, Money.of(12900).compareTo(sale.getCostTotal()), "3 x 4300 batch cost");
        assertEquals(0, Money.of(41100).compareTo(sale.getGrossProfit()));
        assertEquals(0, Quantities.of(17).compareTo(bakery.ctx.inventory().sellableQuantity(bread.getId())));
        assertTrue(sale.getSaleNo().startsWith("S-20261104-"));
        assertTrue(bakery.ctx.sales().receiptText(sale).contains("Rp 54.000"));
    }

    @Test
    void cashMustCoverTheTotalAndNothingIsSavedOtherwise() throws Exception {
        setUp();
        long saves = bakery.saveCount();
        assertThrows(InvalidTransactionException.class, () -> sell(2, new PaymentInfo(PaymentMethod.CASH, Money.of(30000), "")));
        assertEquals(saves, bakery.saveCount());
        assertEquals(0, Quantities.of(20).compareTo(bakery.ctx.inventory().sellableQuantity(bread.getId())));
        assertTrue(bakery.ctx.sales().search(null, null, "", null, null).isEmpty());
    }

    @Test
    void cannotSellMoreThanSellableStock() throws Exception {
        setUp();
        byte[] before = Files.readAllBytes(bakery.ctx.getPaths().getDataFile());
        assertThrows(InsufficientStockException.class, () -> sell(21, new PaymentInfo(PaymentMethod.QRIS, null, "")));
        assertArrayEquals(before, Files.readAllBytes(bakery.ctx.getPaths().getDataFile()));
    }

    @Test
    void expiredBreadIsNeverSold() throws Exception {
        // AC-08: expiresAt == now counts as expired
        setUp();
        bakery.clock.advance(Duration.ofHours(48));
        assertEquals(0, Quantities.ZERO.compareTo(bakery.ctx.inventory().sellableQuantity(bread.getId())));
        InsufficientStockException error = assertThrows(InsufficientStockException.class,
                () -> sell(1, new PaymentInfo(PaymentMethod.QRIS, null, "")));
        assertTrue(error.getMessage().contains("expired"));
    }

    @Test
    void voidReturnsStockSameDayOnlyAndOnlyForTheOwner() throws Exception {
        // AC-09
        setUp();
        Sale sale = sell(2, new PaymentInfo(PaymentMethod.DEBIT_CARD, null, ""));
        bakery.ctx.sales().voidSale(sale.getId(), "Wrong item rung up");

        Sale voided = bakery.ctx.sales().search(null, null, "", null, SaleStatus.VOIDED).get(0);
        assertEquals("Wrong item rung up", voided.getVoidReason());
        assertEquals(0, Quantities.of(20).compareTo(bakery.ctx.inventory().sellableQuantity(bread.getId())));
        long reversals = bakery.ctx.inventory().getMovements(StockKind.PRODUCT, bread.getId(), null, null).stream()
                .filter(m -> m instanceof SaleReversal).count();
        assertEquals(1, reversals);
        assertThrows(InvalidTransactionException.class, () -> bakery.ctx.sales().voidSale(sale.getId(), "again"));

        Sale yesterday = sell(1, new PaymentInfo(PaymentMethod.CASH, Money.of(18000), ""));
        bakery.clock.advance(Duration.ofDays(1));
        assertThrows(InvalidTransactionException.class, () -> bakery.ctx.sales().voidSale(yesterday.getId(), "too late"));

        bakery.createUser("sari", Role.CASHIER);
        bakery.ctx.auth().logout();
        bakery.ctx.getSession().signIn(app.panyaya.model.BakeryData.findById(
                bakery.ctx.getStore().getData().getUsers(), 2));
        assertThrows(AuthorizationException.class, () -> bakery.ctx.sales().voidSale(yesterday.getId(), "cashier"));
        assertThrows(AuthorizationException.class, () -> bakery.ctx.products().createProduct("X", "XX", 1,
                ProductType.MANUFACTURED, Money.of(1), 1, ""));
    }

    @Test
    void wasteLossIsQuantityTimesBatchCost() throws Exception {
        // AC-10 style: 5 pcs x Rp 4,300 = Rp 21,500
        setUp();
        FinishedGoodsBatch batch = bakery.ctx.inventory().getBatches(bread.getId(), false).get(0);
        assertEquals(0, Money.of(21500).compareTo(bakery.ctx.waste().previewLoss(StockKind.PRODUCT, batch.getId(), Quantities.of(5))));

        WasteRecord record = bakery.ctx.waste().recordProductWaste(batch.getId(), Quantities.of(5), WasteReason.DAMAGED, "");
        assertEquals(0, Money.of(21500).compareTo(record.getEstimatedLoss()));
        assertEquals(0, Quantities.of(15).compareTo(bakery.ctx.inventory().getBatches(bread.getId(), false).get(0).getRemainingQuantity()));
    }

    @Test
    void wasteRulesAreEnforced() throws Exception {
        setUp();
        FinishedGoodsBatch batch = bakery.ctx.inventory().getBatches(bread.getId(), false).get(0);
        assertThrows(ValidationException.class, () -> bakery.ctx.waste().recordProductWaste(batch.getId(),
                Quantities.of(21), WasteReason.DAMAGED, ""), "more than remaining");
        assertThrows(ValidationException.class, () -> bakery.ctx.waste().recordProductWaste(batch.getId(),
                Quantities.of(1), WasteReason.OTHER, "bad"), "OTHER needs a 5+ character note");
        assertThrows(ValidationException.class, () -> bakery.ctx.waste().recordProductWaste(batch.getId(),
                Quantities.of("1.5"), WasteReason.DAMAGED, ""), "whole pieces only");
        bakery.ctx.waste().recordProductWaste(batch.getId(), Quantities.of(1), WasteReason.OTHER, "dropped on floor");
    }

    @Test
    void endOfDayWritesOffExpiringBatchesInOneSave() throws Exception {
        setUp();
        // resale water expiring today + bread (48 h) -> only the water is a candidate tonight
        Product waterBottle = bakery.ctx.products().createProduct("Mineral Water", "DRI-WATER", bakery.categoryId("Drinks"),
                ProductType.RESALE, Money.of(5000), 8760, "");
        bakery.ctx.inventory().receiveGoods(bakery.marketSupplierId(), bakery.today(), "", Collections.singletonList(
                new ReceiptLineInput(StockKind.PRODUCT, waterBottle.getId(), Quantities.of(2), Unit.DOZEN, Money.of(72000),
                        "", bakery.today())));
        List<FinishedGoodsBatch> candidates = bakery.ctx.waste().endOfDayCandidates();
        assertEquals(1, candidates.size());
        assertEquals(waterBottle.getId(), candidates.get(0).getItemId());
        assertEquals(0, Quantities.of(24).compareTo(candidates.get(0).getRemainingQuantity()), "2 dozen = 24 pcs");

        long saves = bakery.saveCount();
        List<WasteRecord> records = bakery.ctx.waste().writeOffEndOfDay(Collections.singletonList(candidates.get(0).getId()));
        assertEquals(saves + 1, bakery.saveCount(), "one save for the whole write-off");
        assertEquals(WasteReason.UNSOLD, records.get(0).getReason());
        assertEquals(0, Money.of(72000).compareTo(records.get(0).getEstimatedLoss()));
        assertFalse(bakery.ctx.waste().endOfDayCandidates().contains(candidates.get(0)));
    }
}
