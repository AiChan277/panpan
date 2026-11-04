package app.panyaya;

import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.recipe.RecipeItem;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.model.user.Role;
import app.panyaya.persistence.AppPaths;
import app.panyaya.persistence.StartupInspector;
import app.panyaya.service.PasswordHasher;
import app.panyaya.service.ReceiptLineInput;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Test fixture: a real AppContext on a temporary folder (real JSON files), with a controllable clock
 * and fast password hashing. Signed in as the Owner.
 */
public class TestBakery {

    /** 4 Nov 2026 08:00 in Jakarta (UTC+7). */
    public static final Instant START = Instant.parse("2026-11-04T01:00:00Z");
    public static final String OWNER_PASSWORD = "owner-secret";

    public final Path dir;
    public final MutableClock clock;
    public final AppContext ctx;

    public TestBakery(Path dir) throws Exception {
        this(dir, new MutableClock(START), true);
    }

    private TestBakery(Path dir, MutableClock clock, boolean setup) throws Exception {
        this.dir = dir;
        this.clock = clock;
        this.ctx = new AppContext(new AppPaths(dir), clock, new PasswordHasher(1000));
        StartupInspector.Result result = ctx.getStartupInspector().inspect();
        if (setup) {
            if (result.getState() != StartupInspector.State.FIRST_RUN) {
                throw new IllegalStateException("expected an empty folder, got " + result.getState());
            }
            ctx.auth().setupNewBakery("Test Bakery", "owner", "Owner", OWNER_PASSWORD.toCharArray());
        }
    }

    /** Opens the same folder again, like restarting the application. */
    public TestBakery reopen() throws Exception {
        TestBakery reopened = new TestBakery(dir, clock, false);
        reopened.ctx.auth().login("owner", OWNER_PASSWORD.toCharArray());
        return reopened;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ctx.getStore().getData().getSettings().zone());
    }

    public long categoryId(String name) {
        for (ProductCategory category : ctx.products().getCategories(true)) {
            if (category.getName().equals(name)) {
                return category.getId();
            }
        }
        throw new IllegalArgumentException(name);
    }

    public long marketSupplierId() {
        Supplier supplier = ctx.suppliers().getActiveSuppliers().get(0);
        return supplier.getId();
    }

    public Ingredient ingredient(String code, String name, Unit unit, String minimum, boolean tracksExpiry)
            throws Exception {
        return ctx.ingredients().create(code, name, unit, Quantities.of(minimum), tracksExpiry, true, null, "",
                null, null, null);
    }

    public Product manufactured(String name, String sku, long price, int shelfLifeHours) throws Exception {
        return ctx.products().createProduct(name, sku, categoryId("Bread"), ProductType.MANUFACTURED, Money.of(price),
                shelfLifeHours, "");
    }

    /** Receives one ingredient line from the market supplier. */
    public void receive(Ingredient ingredient, String quantity, Unit unit, long totalCost, LocalDate expiry)
            throws Exception {
        ctx.inventory().receiveGoods(marketSupplierId(), today(), "INV-1", Collections.singletonList(
                new ReceiptLineInput(StockKind.INGREDIENT, ingredient.getId(), Quantities.of(quantity), unit,
                        Money.of(totalCost), "", expiry)));
    }

    public Recipe activeRecipe(String name, Product product, String yield, List<RecipeItem> items) throws Exception {
        Recipe recipe = ctx.recipes().create(name, product.getId(), Quantities.of(yield), items, 30, 20, null, "");
        ctx.recipes().activate(recipe.getId(), false);
        return ctx.recipes().getRecipe(recipe.getId());
    }

    public static RecipeItem item(Ingredient ingredient, String quantity, Unit unit) throws Exception {
        return new RecipeItem(ingredient.getId(), Quantities.of(quantity), unit, "");
    }

    public static BigDecimal qty(String value) {
        return Quantities.of(value);
    }

    public void createUser(String username, Role role) throws Exception {
        ctx.users().createUser(username, username, role, "temp-pass1".toCharArray());
    }

    public long saveCount() {
        return ctx.getStore().getData().getSaveCount();
    }
}
