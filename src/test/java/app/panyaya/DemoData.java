package app.panyaya;

import app.panyaya.model.Money;
import app.panyaya.model.Quantities;
import app.panyaya.model.StockKind;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.FinishedGoodsBatch;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductType;
import app.panyaya.model.production.ProductionOrder;
import app.panyaya.model.recipe.Recipe;
import app.panyaya.model.sales.Cart;
import app.panyaya.model.sales.PaymentInfo;
import app.panyaya.model.sales.PaymentMethod;
import app.panyaya.model.sales.Sale;
import app.panyaya.model.user.Role;
import app.panyaya.model.waste.WasteReason;
import app.panyaya.persistence.AppPaths;
import app.panyaya.persistence.StartupInspector;
import app.panyaya.service.PasswordHasher;
import app.panyaya.service.ReceiptLineInput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a realistic demo bakery for presentations and manual UI testing. NOT part of the
 * application (test sources only, never packaged).
 *
 * <pre>
 * java -cp target/classes;target/test-classes;target/libs/* app.panyaya.DemoData C:\temp\panyaya-demo
 * then start the app with -Dpanyaya.home=C:\temp\panyaya-demo
 * Accounts: owner / owner-demo-1, baker / baker-demo-1, cashier / cashier-demo-1
 * </pre>
 */
public final class DemoData {

    private DemoData() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args.length > 0 ? args[0] : "local-data");
        if (Files.exists(dir.resolve("data").resolve(AppPaths.DATA_FILE_NAME))) {
            System.out.println("Demo data already exists in " + dir.toAbsolutePath());
            return;
        }
        AppContext ctx = new AppContext(new AppPaths(dir), Clock.systemUTC(), new PasswordHasher());
        if (ctx.getStartupInspector().inspect().getState() != StartupInspector.State.FIRST_RUN) {
            throw new IllegalStateException("folder is not empty");
        }
        ctx.auth().setupNewBakery("Toko Roti Pan-ya Ya", "owner", "Ibu Sari (Owner)", "owner-demo-1".toCharArray());
        ctx.settings().updateProfile("Toko Roti Pan-ya Ya", "Jl. Merdeka No. 12, Bandung", "022-1234567",
                "Terima kasih! Thank you for your visit.");
        ctx.users().createUser("baker", "Pak Budi (Baker)", Role.BAKER, "baker-demo-1".toCharArray());
        ctx.users().createUser("cashier", "Rina (Cashier)", Role.CASHIER, "cashier-demo-1".toCharArray());
        ctx.seed().importStarterRecipes();
        for (Recipe recipe : ctx.recipes().search("", false)) {
            ctx.recipes().activate(recipe.getId(), true);
        }
        long supplier = ctx.suppliers().create("CV Tepung Jaya", "Pak Hadi", "0812-3456-7890", "sales@tepungjaya.id",
                "Jl. Industri 5, Bandung", "Delivers Monday and Thursday").getId();
        ZoneId zone = ctx.settings().getSettings().zone();
        LocalDate today = LocalDate.now(zone);

        List<ReceiptLineInput> lines = new ArrayList<>();
        lines.add(line(ctx, "FLOUR-BREAD", "25", Unit.KG, 312500, today.plusDays(90)));
        lines.add(line(ctx, "FLOUR-AP", "10", Unit.KG, 115000, today.plusDays(90)));
        lines.add(line(ctx, "SUGAR", "10", Unit.KG, 160000, null));
        lines.add(line(ctx, "SUGAR-ICING", "2", Unit.KG, 44000, today.plusDays(120)));
        lines.add(line(ctx, "YEAST", "500", Unit.G, 45000, today.plusDays(60)));
        lines.add(line(ctx, "SALT", "1", Unit.KG, 12000, null));
        lines.add(line(ctx, "BUTTER", "2", Unit.KG, 180000, today.plusDays(2)));   // expiring soon
        lines.add(line(ctx, "MILK-POWDER", "1", Unit.KG, 95000, today.plusDays(150)));
        lines.add(line(ctx, "EGG", "5", Unit.DOZEN, 120000, today.plusDays(14)));
        lines.add(line(ctx, "CHOC-FILL", "1", Unit.KG, 85000, today.plusDays(60)));
        lines.add(line(ctx, "OIL", "2", Unit.L, 36000, today.plusDays(180)));
        ctx.inventory().receiveGoods(supplier, today, "TJ-2026-0412", lines);
        // a second, later-expiring butter lot (FEFO will use the first one first)
        List<ReceiptLineInput> more = new ArrayList<>();
        more.add(line(ctx, "BUTTER", "1", Unit.KG, 92000, today.plusDays(20)));
        ctx.inventory().receiveGoods(ctx.suppliers().getActiveSuppliers().get(0).getId(), today, "", more);

        Product water = ctx.products().createProduct("Mineral Water 600 mL", "DRI-WATER", categoryId(ctx, "Drinks"),
                ProductType.RESALE, Money.of(5000), 8760, "Bottled water");
        List<ReceiptLineInput> drinks = new ArrayList<>();
        drinks.add(new ReceiptLineInput(StockKind.PRODUCT, water.getId(), Quantities.of(2), Unit.DOZEN, Money.of(72000),
                "", today.plusDays(200)));
        ctx.inventory().receiveGoods(supplier, today, "", drinks);

        // the demo product the presentation creates by hand is NOT pre-created: Japanese Milk Bread
        produce(ctx, "BRE-WHITE", 12, 11, 1);
        produce(ctx, "BRE-CHOCBUN", 24, 23, 1);
        produce(ctx, "SNA-DONUT", 32, 32, 0);
        produce(ctx, "PAS-CROIS", 20, 18, 2);
        ProductionOrder planned = ctx.production().plan(product(ctx, "BRE-WHITE").getId(),
                ctx.recipes().getActiveRecipesFor(product(ctx, "BRE-WHITE").getId()).get(0).getId(), 24, today.plusDays(1));

        sell(ctx, PaymentMethod.CASH, Money.of(50000), "BRE-WHITE", 1, "BRE-CHOCBUN", 3);
        sell(ctx, PaymentMethod.QRIS, null, "SNA-DONUT", 6, "DRI-WATER", 2);
        sell(ctx, PaymentMethod.CASH, Money.of(100000), "PAS-CROIS", 4, "BRE-CHOCBUN", 2);
        sell(ctx, PaymentMethod.DEBIT_CARD, null, "BRE-WHITE", 2);
        Sale mistake = sell(ctx, PaymentMethod.CASH, Money.of(20000), "SNA-DONUT", 2);
        ctx.sales().voidSale(mistake.getId(), "Rung up twice by mistake");
        sell(ctx, PaymentMethod.QRIS, null, "PAS-CROIS", 3, "DRI-WATER", 1);

        FinishedGoodsBatch donuts = ctx.inventory().getBatches(product(ctx, "SNA-DONUT").getId(), false).get(0);
        ctx.waste().recordProductWaste(donuts.getId(), Quantities.of(2), WasteReason.DAMAGED, "dropped on the floor");
        ctx.shutdown();
        System.out.println("Demo bakery created in " + dir.toAbsolutePath() + " (planned order " + planned.getOrderNo() + ")");
    }

    private static ReceiptLineInput line(AppContext ctx, String code, String qty, Unit unit, long cost, LocalDate expiry) {
        for (Ingredient ingredient : ctx.ingredients().getActiveIngredients()) {
            if (ingredient.getCode().equals(code)) {
                return new ReceiptLineInput(StockKind.INGREDIENT, ingredient.getId(), Quantities.of(qty), unit,
                        Money.of(cost), "", expiry);
            }
        }
        throw new IllegalArgumentException(code);
    }

    private static long categoryId(AppContext ctx, String name) {
        return ctx.products().getCategories(false).stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(IllegalStateException::new).getId();
    }

    private static Product product(AppContext ctx, String sku) {
        return ctx.products().getActiveProducts().stream().filter(p -> p.getSku().equals(sku)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(sku));
    }

    private static void produce(AppContext ctx, String sku, int planned, int good, int rejected) throws Exception {
        Product product = product(ctx, sku);
        Recipe recipe = ctx.recipes().getActiveRecipesFor(product.getId()).get(0);
        LocalDate today = LocalDate.now(ctx.settings().getSettings().zone());
        ProductionOrder order = ctx.production().plan(product.getId(), recipe.getId(), planned, today);
        ctx.production().start(order.getId());
        ctx.production().complete(order.getId(), good, rejected);
    }

    private static Sale sell(AppContext ctx, PaymentMethod method, java.math.BigDecimal cash, Object... items)
            throws Exception {
        Cart cart = new Cart();
        for (int i = 0; i < items.length; i += 2) {
            cart.add(product(ctx, (String) items[i]), (Integer) items[i + 1]);
        }
        return ctx.sales().checkout(cart, new PaymentInfo(method, cash, method == PaymentMethod.OTHER ? "demo" : ""));
    }
}
