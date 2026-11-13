package app.panyaya;

import app.panyaya.persistence.AppPaths;
import app.panyaya.persistence.DataStorage;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.DataValidator;
import app.panyaya.persistence.FileDataStorage;
import app.panyaya.persistence.JsonCodec;
import app.panyaya.persistence.StartupInspector;
import app.panyaya.service.AuthService;
import app.panyaya.service.BackupService;
import app.panyaya.service.CsvService;
import app.panyaya.service.DashboardService;
import app.panyaya.service.IngredientService;
import app.panyaya.service.InventoryService;
import app.panyaya.service.PasswordHasher;
import app.panyaya.service.ProductService;
import app.panyaya.service.ProductionService;
import app.panyaya.service.RecipeService;
import app.panyaya.service.ReportService;
import app.panyaya.service.SalesService;
import app.panyaya.service.SeedService;
import app.panyaya.service.Session;
import app.panyaya.service.SettingsService;
import app.panyaya.service.SupplierService;
import app.panyaya.service.UserService;
import app.panyaya.service.WasteService;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Composition root: creates every object of the application once and connects them
 * ("manual dependency injection" - no framework). The UI receives this object and asks it for the
 * services it needs. Tests create an AppContext on a temporary folder with a fixed clock.
 */
public class AppContext {

    public static final String APP_NAME = "Pan-ya Ya";
    public static final String APP_VERSION = "1.0.1";

    private final AppPaths paths;
    private final Clock clock;
    private final DataStorage storage;
    private final DataStore store;
    private final Session session;
    private final StartupInspector startupInspector;

    private final AuthService authService;
    private final UserService userService;
    private final ProductService productService;
    private final IngredientService ingredientService;
    private final SupplierService supplierService;
    private final InventoryService inventoryService;
    private final RecipeService recipeService;
    private final ProductionService productionService;
    private final SalesService salesService;
    private final WasteService wasteService;
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final BackupService backupService;
    private final CsvService csvService;
    private final SettingsService settingsService;
    private final SeedService seedService;

    /** Background threads for long work (backups, reports, CSV). Daemon threads end with the app. */
    private final ExecutorService backgroundExecutor;

    public AppContext(AppPaths paths, Clock clock, PasswordHasher hasher) {
        this(paths, new FileDataStorage(paths.getDataFile()), clock, hasher);
    }

    public AppContext(AppPaths paths, DataStorage storage, Clock clock, PasswordHasher hasher) {
        this.paths = paths;
        this.clock = clock;
        this.storage = storage;
        this.store = new DataStore(storage, new JsonCodec(), new DataValidator(), clock, APP_VERSION);
        this.session = new Session(store);
        this.startupInspector = storage instanceof FileDataStorage
                ? new StartupInspector(paths, (FileDataStorage) storage, store) : null;

        this.authService = new AuthService(store, session, clock, hasher);
        this.userService = new UserService(store, session, clock, hasher);
        this.productService = new ProductService(store, session, clock);
        this.ingredientService = new IngredientService(store, session, clock);
        this.supplierService = new SupplierService(store, session, clock);
        this.inventoryService = new InventoryService(store, session, clock);
        this.recipeService = new RecipeService(store, session, clock);
        this.productionService = new ProductionService(store, session, clock);
        this.salesService = new SalesService(store, session, clock);
        this.wasteService = new WasteService(store, session, clock);
        this.dashboardService = new DashboardService(store, session, clock);
        this.reportService = new ReportService(store, session, clock);
        this.backupService = new BackupService(store, session, clock, paths, APP_VERSION);
        this.csvService = new CsvService(store, session, clock);
        this.settingsService = new SettingsService(store, session, clock);
        this.seedService = new SeedService(store, session, clock);

        this.backgroundExecutor = Executors.newFixedThreadPool(2, new NamedDaemonThreadFactory("panyaya-worker"));
    }

    /** Stops background threads, waiting briefly for running work (e.g. a backup) to finish. */
    public void shutdown() {
        backgroundExecutor.shutdown();
        try {
            backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Gives worker threads readable names ("panyaya-worker-1") for logs and debugging. */
    private static class NamedDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    public AppPaths getPaths() {
        return paths;
    }

    public Clock getClock() {
        return clock;
    }

    public DataStorage getStorage() {
        return storage;
    }

    public DataStore getStore() {
        return store;
    }

    public Session getSession() {
        return session;
    }

    public StartupInspector getStartupInspector() {
        return startupInspector;
    }

    public AuthService auth() {
        return authService;
    }

    public UserService users() {
        return userService;
    }

    public ProductService products() {
        return productService;
    }

    public IngredientService ingredients() {
        return ingredientService;
    }

    public SupplierService suppliers() {
        return supplierService;
    }

    public InventoryService inventory() {
        return inventoryService;
    }

    public RecipeService recipes() {
        return recipeService;
    }

    public ProductionService production() {
        return productionService;
    }

    public SalesService sales() {
        return salesService;
    }

    public WasteService waste() {
        return wasteService;
    }

    public DashboardService dashboard() {
        return dashboardService;
    }

    public ReportService reports() {
        return reportService;
    }

    public BackupService backups() {
        return backupService;
    }

    public CsvService csv() {
        return csvService;
    }

    public SettingsService settings() {
        return settingsService;
    }

    public SeedService seed() {
        return seedService;
    }

    public ExecutorService getBackgroundExecutor() {
        return backgroundExecutor;
    }
}
