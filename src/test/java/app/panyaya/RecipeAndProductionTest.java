package app.panyaya;

import app.panyaya.exception.InsufficientStockException;
import app.panyaya.exception.InvalidRecipeException;
import app.panyaya.exception.InvalidTransactionException;
import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.IngredientLot;
import app.panyaya.model.product.Product;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.production.ProductionStatus;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.recipe.RecipeItem;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.model.waste.WasteRecord;
import app.panyaya.service.CostEstimate;
import app.panyaya.service.MaterialRequirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static app.panyaya.TestBakery.item;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeAndProductionTest {

    @TempDir
    Path dir;

    private TestBakery bakery;
    private Ingredient flour;
    private Ingredient butter;
    private Ingredient water;
    private Product bun;
    private Recipe recipe;

    /** Sweet bun: 12 pieces need 500 g flour, 60 g butter, 250 mL water (water not stock-tracked). */
    private void setUp() throws Exception {
        bakery = new TestBakery(dir);
        flour = bakery.ingredient("FLOUR", "Flour", Unit.G, "1000", true);
        butter = bakery.ingredient("BUTTER", "Butter", Unit.G, "500", true);
        water = bakery.ctx.ingredients().create("WATER", "Water", Unit.ML, Quantities.ZERO, false, false, null, "",
                null, null, null);
        bun = bakery.manufactured("Sweet Bun", "BRE-BUN", 8000, 36);
        recipe = bakery.activeRecipe("Sweet Bun", bun, "12", Arrays.asList(
                item(flour, "0.5", Unit.KG), item(butter, "60", Unit.G), item(water, "250", Unit.ML)));
    }

    @Test
    void scalesRecipeForAnyTargetQuantity() throws Exception {
        setUp();
        List<MaterialRequirement> for48 = bakery.ctx.recipes().calculateRequirements(recipe, Quantities.of(48));
        assertEquals(0, Quantities.of(2000).compareTo(for48.get(0).getRequired()), "0.5 kg x 4 = 2000 g");
        assertEquals(0, Quantities.of(240).compareTo(for48.get(1).getRequired()));

        List<MaterialRequirement> for18 = bakery.ctx.recipes().calculateRequirements(recipe, Quantities.of(18));
        assertEquals(0, Quantities.of(750).compareTo(for18.get(0).getRequired()), "x 1.5");
        assertEquals(0, Quantities.of(90).compareTo(for18.get(1).getRequired()));
        assertTrue(for18.get(0).isShort(), "no flour received yet");
        assertFalse(for18.get(2).isShort(), "water is not stock-tracked, never short");
    }

    @Test
    void costEstimateIsMarkedIncompleteWhenACostIsUnknown() throws Exception {
        setUp();
        CostEstimate unknown = bakery.ctx.recipes().estimateCost(recipe);
        assertFalse(unknown.isComplete());
        assertEquals(2, unknown.getUnknownCostIngredients().size());

        bakery.receive(flour, "10", Unit.KG, 120000, bakery.today().plusDays(30));   // Rp 12/g
        bakery.receive(butter, "1", Unit.KG, 90000, bakery.today().plusDays(10));    // Rp 90/g
        CostEstimate known = bakery.ctx.recipes().estimateCost(bakery.ctx.recipes().getRecipe(recipe.getId()));
        assertTrue(known.isComplete());
        // 500 g x 12 + 60 g x 90 = 6000 + 5400 = 11400 per batch of 12 -> 950 per bun
        assertEquals(0, Money.of(11400).compareTo(known.getBatchCost()));
        assertEquals(0, Money.of(950).compareTo(known.getCostPerUnit()));
    }

    @Test
    void activationRequiresValidRecipeAndTemplateReview() throws Exception {
        setUp();
        Recipe empty = bakery.ctx.recipes().create("Empty", bun.getId(), Quantities.of(1), Collections.<RecipeItem>emptyList(),
                0, 0, null, "");
        assertThrows(InvalidRecipeException.class, () -> bakery.ctx.recipes().activate(empty.getId(), false));

        int imported = bakery.ctx.seed().importStarterRecipes();
        assertEquals(4, imported);
        Recipe template = bakery.ctx.recipes().search("Croissant", false).get(0);
        assertTrue(template.isTemplate());
        assertThrows(InvalidRecipeException.class, () -> bakery.ctx.recipes().activate(template.getId(), false));
        bakery.ctx.recipes().activate(template.getId(), true);
        assertTrue(bakery.ctx.recipes().getRecipe(template.getId()).isActive());
    }

    @Test
    void startIsRefusedWithShortageListAndNothingChanges() throws Exception {
        // AC-04: flour 900 g on hand, recipe needs 1000 g
        setUp();
        bakery.receive(flour, "900", Unit.G, 10800, bakery.today().plusDays(30));
        ProductionOrder order = bakery.ctx.production().plan(bun.getId(), recipe.getId(), 24, bakery.today());
        byte[] fileBefore = Files.readAllBytes(bakery.ctx.getPaths().getDataFile());

        InsufficientStockException error = assertThrows(InsufficientStockException.class,
                () -> bakery.ctx.production().start(order.getId()));
        assertEquals(2, error.getShortages().size(), "flour and butter are both reported");
        assertTrue(error.getShortages().get(0).startsWith("Flour: short by 100 g"));

        assertArrayEquals(fileBefore, Files.readAllBytes(bakery.ctx.getPaths().getDataFile()));
        assertEquals(ProductionStatus.PLANNED, bakery.ctx.production().getOrder(order.getId()).getStatus());
        assertTrue(bakery.ctx.inventory().getMovements(StockKind.INGREDIENT, flour.getId(), null, null).size() == 1,
                "only the receipt, no consumption");
    }

    @Test
    void startConsumesFefoAndCompleteCreatesBatchAndRejectWaste() throws Exception {
        setUp();
        // two butter lots: the one expiring first must be emptied first (AC-05)
        bakery.receive(flour, "10", Unit.KG, 120000, bakery.today().plusDays(30));
        bakery.receive(butter, "100", Unit.G, 9000, bakery.today().plusDays(20));
        bakery.receive(butter, "200", Unit.G, 20000, bakery.today().plusDays(5));

        ProductionOrder order = bakery.ctx.production().plan(bun.getId(), recipe.getId(), 48, bakery.today());
        ProductionOrder started = bakery.ctx.production().start(order.getId());
        assertEquals(ProductionStatus.IN_PROGRESS, started.getStatus());

        // butter needed 240 g: 200 g from the early lot (Rp 100/g) + 40 g from the late lot (Rp 90/g)
        List<IngredientLot> butterLots = new ArrayList<>(bakery.ctx.inventory().getIngredientLots(butter.getId(), true));
        for (IngredientLot lot : butterLots) {
            if (lot.getExpiryDate().equals(bakery.today().plusDays(5))) {
                assertTrue(lot.isDepleted(), "early lot emptied first");
            } else {
                assertEquals(0, Quantities.of(60).compareTo(lot.getRemainingQuantity()));
            }
        }
        // material cost = 2000 g x 12 + 200 x 100 + 40 x 90 = 24000 + 20000 + 3600 = 47600
        assertEquals(0, Money.of(47600).compareTo(started.getMaterialCost()));

        // AC-06 (adapted): 45 good + 3 rejected
        ProductionOrder done = bakery.ctx.production().complete(order.getId(), 45, 3);
        assertEquals(ProductionStatus.COMPLETED, done.getStatus());
        // unit cost spread over all 48 pieces: 47600 / 48 = 991.6667
        assertEquals(0, Money.of("991.6667").compareTo(done.getUnitCost()));

        List<FinishedGoodsBatch> batches = bakery.ctx.inventory().getBatches(bun.getId(), false);
        assertEquals(1, batches.size());
        assertEquals(0, Quantities.of(45).compareTo(batches.get(0).getRemainingQuantity()));
        assertEquals(TestBakery.START.plusSeconds(36 * 3600), batches.get(0).getExpiresAt());

        List<WasteRecord> waste = bakery.ctx.waste().search(null, null, null, WasteReason.PRODUCTION_FAILURE);
        assertEquals(1, waste.size());
        assertEquals(0, Quantities.of(3).compareTo(waste.get(0).getQuantity()));
        assertEquals(0, Money.of("2975.0001").compareTo(waste.get(0).getEstimatedLoss()));
    }

    @Test
    void illegalTransitionsAreRefused() throws Exception {
        setUp();
        ProductionOrder order = bakery.ctx.production().plan(bun.getId(), recipe.getId(), 12, bakery.today());
        assertThrows(InvalidTransactionException.class, () -> bakery.ctx.production().complete(order.getId(), 12, 0));
        bakery.ctx.production().cancel(order.getId(), "changed plan");
        assertThrows(InvalidTransactionException.class, () -> bakery.ctx.production().start(order.getId()));
    }

    @Test
    void failedBatchRecordsTheWholeMaterialCostAsWaste() throws Exception {
        setUp();
        bakery.receive(flour, "1", Unit.KG, 12000, bakery.today().plusDays(30));
        bakery.receive(butter, "100", Unit.G, 9000, bakery.today().plusDays(30));
        ProductionOrder order = bakery.ctx.production().plan(bun.getId(), recipe.getId(), 12, bakery.today());
        bakery.ctx.production().start(order.getId());
        ProductionOrder failed = bakery.ctx.production().markFailed(order.getId(), "Oven too hot, all burnt");

        assertEquals(ProductionStatus.FAILED, failed.getStatus());
        WasteRecord waste = bakery.ctx.waste().search(null, null, null, null).get(0);
        // 500 g x 12 + 60 g x 90 = 11400
        assertEquals(0, Money.of(11400).compareTo(waste.getEstimatedLoss()));
        assertTrue(bakery.ctx.inventory().getBatches(bun.getId(), true).isEmpty(), "no finished goods");
    }

    @Test
    void editingARecipeDoesNotChangeHistoricalProduction() throws Exception {
        setUp();
        bakery.receive(flour, "5", Unit.KG, 60000, bakery.today().plusDays(30));
        bakery.receive(butter, "500", Unit.G, 45000, bakery.today().plusDays(30));
        ProductionOrder order = bakery.ctx.production().plan(bun.getId(), recipe.getId(), 12, bakery.today());
        bakery.ctx.production().start(order.getId());
        bakery.ctx.production().complete(order.getId(), 12, 0);
        java.math.BigDecimal costBefore = bakery.ctx.production().getOrder(order.getId()).getUnitCost();

        bakery.ctx.recipes().update(recipe.getId(), "Sweet Bun", bun.getId(), Quantities.of(12),
                Arrays.asList(item(flour, "1", Unit.KG), item(butter, "100", Unit.G)), 30, 20, null, "more flour");

        ProductionOrder historical = bakery.ctx.production().getOrder(order.getId());
        assertEquals(1, historical.getRecipeVersion());
        assertEquals(2, bakery.ctx.recipes().getRecipe(recipe.getId()).getVersion());
        assertEquals(0, costBefore.compareTo(historical.getUnitCost()));
        assertEquals(0, Quantities.of(500).compareTo(historical.getMaterials().get(0).getQuantity()));
    }
}
