package app.panyaya;

import app.panyaya.exception.InsufficientStockException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.AdjustmentReason;
import app.panyaya.model.inventory.FefoAllocator;
import app.panyaya.model.inventory.Allocation;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.inventory.StockReceipt;
import app.panyaya.service.IngredientService;
import app.panyaya.service.IngredientStock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTest {

    @TempDir
    Path dir;

    @Test
    void receivingConvertsUnitsAndComputesUnitCost() throws Exception {
        // AC-03: 25 kg flour for Rp 312,500 -> lot of 25,000 g at Rp 12.5/g
        TestBakery bakery = new TestBakery(dir);
        Ingredient flour = bakery.ingredient("FLOUR", "Flour", Unit.G, "10000", true);
        bakery.receive(flour, "25", Unit.KG, 312500, bakery.today().plusDays(60));

        List<IngredientLot> lots = bakery.ctx.inventory().getIngredientLots(flour.getId(), false);
        assertEquals(1, lots.size());
        IngredientLot lot = lots.get(0);
        assertEquals(0, Quantities.of(25000).compareTo(lot.getRemainingQuantity()));
        assertEquals(0, Money.of("12.5").compareTo(lot.getUnitCost()));

        List<InventoryMovement> ledger = bakery.ctx.inventory().getMovements(StockKind.INGREDIENT, flour.getId(), null, null);
        assertEquals(1, ledger.size());
        assertTrue(ledger.get(0) instanceof StockReceipt);
        assertEquals(0, Quantities.of(25000).compareTo(ledger.get(0).getSignedQuantity()));
    }

    @Test
    void expiryIsRequiredForTrackedIngredientsAndCrossDimensionUnitsAreRefused() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        Ingredient butter = bakery.ingredient("BUTTER", "Butter", Unit.G, "1000", true);
        assertThrows(ValidationException.class, () -> bakery.receive(butter, "1", Unit.KG, 90000, null));
        assertThrows(ValidationException.class, () -> bakery.receive(butter, "1", Unit.L, 90000, bakery.today()));
        assertTrue(bakery.ctx.inventory().getIngredientLots(butter.getId(), true).isEmpty());
    }

    @Test
    void stockCanNeverBecomeNegative() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        Ingredient sugar = bakery.ingredient("SUGAR", "Sugar", Unit.G, "0", false);
        bakery.receive(sugar, "1000", Unit.G, 15000, null);
        IngredientLot lot = bakery.ctx.inventory().getIngredientLots(sugar.getId(), false).get(0);

        assertThrows(InsufficientStockException.class, () -> bakery.ctx.inventory().adjustStock(StockKind.INGREDIENT,
                lot.getId(), false, Quantities.of(1500), AdjustmentReason.COUNT_CORRECTION, "counted less"));

        bakery.ctx.inventory().adjustStock(StockKind.INGREDIENT, lot.getId(), false, Quantities.of(200),
                AdjustmentReason.COUNT_CORRECTION, "counted 800 g");
        IngredientLot after = bakery.ctx.inventory().getIngredientLots(sugar.getId(), false).get(0);
        assertEquals(0, Quantities.of(800).compareTo(after.getRemainingQuantity()));
    }

    @Test
    void lowStockAndExpiredLotsAreDetected() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        Ingredient yeast = bakery.ingredient("YEAST", "Yeast", Unit.G, "500", true);
        bakery.receive(yeast, "300", Unit.G, 30000, bakery.today().plusDays(2));
        ZoneId zone = bakery.ctx.getStore().getData().getSettings().zone();

        IngredientStock stock = IngredientService.stockOf(bakery.ctx.getStore().getData(), yeast, bakery.clock.instant(), zone);
        assertEquals(IngredientStock.Status.LOW, stock.getStatus());

        bakery.clock.advance(Duration.ofDays(3)); // the lot expires
        IngredientStock later = IngredientService.stockOf(bakery.ctx.getStore().getData(), yeast, bakery.clock.instant(), zone);
        assertEquals(IngredientStock.Status.OUT, later.getStatus(), "expired stock is not usable");
        assertEquals(0, Quantities.of(300).compareTo(later.getExpiredQuantity()));
    }

    @Test
    void fefoUsesTheEarliestExpiringLotFirstAndSkipsExpiredLots() throws Exception {
        // AC-05 at the domain level
        ZoneId zone = ZoneId.of("Asia/Jakarta");
        Instant now = TestBakery.START;
        IngredientLot late = lot(1, "LATE", "20", now, "2026-11-20");
        IngredientLot early = lot(2, "EARLY", "10", now, "2026-11-10");
        IngredientLot expired = lot(3, "OLD", "50", now, "2026-11-01");
        List<IngredientLot> lots = new ArrayList<>();
        lots.add(late);
        lots.add(early);
        lots.add(expired);

        FefoAllocator<IngredientLot> allocator = new FefoAllocator<>();
        List<Allocation<IngredientLot>> result = allocator.allocate(lots, Quantities.of(15), now, zone);
        assertEquals(2, result.size());
        assertSame(early, result.get(0).getLot());
        assertEquals(0, Quantities.of(10).compareTo(result.get(0).getQuantity()));
        assertSame(late, result.get(1).getLot());
        assertEquals(0, Quantities.of(5).compareTo(result.get(1).getQuantity()));

        assertEquals(0, Quantities.of(30).compareTo(allocator.available(lots, now, zone)));
        assertThrows(InsufficientStockException.class, () -> allocator.allocate(lots, Quantities.of(31), now, zone));
        assertFalse(expired.isUsableAt(now, zone));
    }

    /** Builds a lot that already holds stock (filled through a receipt movement, as in the app). */
    private static IngredientLot lot(long id, String code, String qty, Instant at, String expiry) throws Exception {
        IngredientLot lot = new IngredientLot(id, 1, code, Quantities.of(qty), Money.of(10), at,
                java.time.LocalDate.parse(expiry), null, 0);
        new StockReceipt(id, at, java.time.LocalDate.parse("2026-11-01"), lot, Quantities.of(qty), 1, "test", 0)
                .applyTo(lot, Instant.parse("2026-10-01T00:00:00Z"), ZoneId.of("UTC"));
        return lot;
    }
}
