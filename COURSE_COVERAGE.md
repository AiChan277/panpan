# Course coverage - Java OOP topics in Pan-ya Ya

Every topic of the syllabus is used in a real feature. Paths are relative to
`src/main/java/app/panyaya/`.

## 1. OOP Introduction - object-oriented decomposition

The bakery is modelled as objects that own their data and rules:
`Product`, `Ingredient`, `Recipe`, `IngredientLot`, `FinishedGoodsBatch`, `ProductionOrder`, `Sale`,
`WasteRecord`, `Supplier`, `User`. Layers have clear duties:
UI (`ui/`) → services (`service/`) → model (`model/`) → persistence (`persistence/`).

**Demo:** trace one sale: `PosController.onPay` → `PaymentDialog` → `SalesService.checkout` →
`FefoAllocator.allocate` → `SaleIssue.applyTo` → `DataStore.execute` → `FileDataStorage.write` →
open `pan-ya-ya.json` and find the sale.

## 2. Class & Object

| Where | Example |
|---|---|
| `model/product/Product.java` | constructor validates name, SKU, price; `new Product(...)` in `ProductService.createProduct` |
| `model/recipe/Recipe.java` | object created by the recipe editor; `duplicate()` creates a new object |
| `model/sales/Cart.java` | in-memory object that lives only until checkout |

**Demo:** Products → "+ New product" → "Japanese Milk Bread" → a `Product` object is constructed
and saved; it appears in `pan-ya-ya.json`.

## 3. Java Variables

| Kind | Where |
|---|---|
| Constants (`static final`) | `Money.SCALE`, `Money.ROUNDING`, `Quantities.SCALE`, `BakeryData.CURRENT_SCHEMA_VERSION`, `PasswordHasher.DEFAULT_ITERATIONS`, `User.MAX_FAILED_ATTEMPTS` |
| Static variables | `MainController.NAVIGATION` (list shared by all instances), `TextUtil.SKU_PATTERN` |
| Instance variables | all private fields of the model classes, e.g. `StockLot.remainingQuantity` |
| Local variables | `FefoAllocator.allocate` (`stillNeeded`, `take`), `ProductionService.start` |
| Primitive types | `int plannedQuantity`, `long id`, `boolean active`, `double` only for chart drawing |
| Reference types | `BigDecimal` for all money and quantities, `Instant`, `LocalDate`, `String`, enums |

Money is **never** `double`: `TextAndNumberTest.moneyArithmeticIsExactAndRoundsHalfUp` shows
0.1 + 0.2 = 0.3000 exactly.

## 4. Inheritance

| Hierarchy | Why it is real |
|---|---|
| `model/BaseEntity` ← every entity | shared id and `equals/hashCode` |
| `model/inventory/StockLot` ← `IngredientLot`, `FinishedGoodsBatch` | shared quantity/cost logic; expiry differs (date vs exact time) |
| `model/inventory/InventoryMovement` ← `StockReceipt`, `ProductionConsumption`, `ProductionOutput`, `SaleIssue`, `SaleReversal`, `WasteWriteOff`, `StockAdjustment` | shared ledger fields; different direction and rules |
| `report/ReportGenerator` ← 9 reports | shared generation pipeline |
| `exception/PanyaException` ← `BusinessException` ← `ValidationException`, `InsufficientStockException`, ...; ← `PersistenceException` ← `DataCorruptedException`, `DataIntegrityException` | UI treats categories differently |
| `service/BaseService` ← all services | shared time, user, audit and `post()` helpers |
| `ui/common/BaseController` ← screen controllers | shared `attempt()` error handling |

Rejected on purpose: `Employee → Cashier/Baker`. A role is data (`model/user/Role`) that a `User`
*has* (composition), because a person's role can change.

## 5. Encapsulation

- All fields are `private`; there are no general setters for business-critical state.
- `Product.changePrice()` validates and records a `PriceChange` row instead of a `setPrice`.
- `StockLot.increase()/decrease()` are **package-private**: only `InventoryMovement` subclasses
  (same package) can change stock, so every change is in the ledger. `decrease()` refuses to go
  below zero.
- `ProductionOrder` status only changes through `start/complete/fail/cancel`, which check the
  allowed transitions (`InvalidTransactionException` otherwise).
- `Sale.complete()` locks the sale; `Sale.addLine()` throws once it is completed.
- Lists are exposed read-only: `Product.getPriceHistory()`, `Recipe.getItems()` return
  `Collections.unmodifiableList`; `Cart.getLines()` returns a copy.

**Demo:** try to start production with too little flour: refused, stock unchanged.

## 6. Polymorphism

- `InventoryMovement.applyTo(lot, now, zone)` is the same call for every movement. The concrete
  object decides: `isIncoming()` (direction) and `checkAllowed()` (overridden by
  `ProductionConsumption` and `SaleIssue` to refuse expired lots; `WasteWriteOff` allows them).
  `StockAdjustment` even decides per object (`increase` field).
- `StockLot.isExpiredAt()` - date-based in `IngredientLot`, instant-based in `FinishedGoodsBatch`;
  `FefoAllocator` works without knowing which.
- `ReportsController` holds `ReportGenerator` references and calls `generate()`; each subclass
  fills its own rows (`DailySalesReport.fill`, `WasteReport.fill`, ...).
- `getTypeName()` gives each movement its label on the ledger screen.
- JSON: the `"type"` property maps each movement back to its subclass (`InventoryMovementMixin`).

## 7. Abstraction

| Abstract type | Implementations / use |
|---|---|
| interface `persistence/DataStorage` | `FileDataStorage` (real file); tests use an in-memory failing storage |
| abstract class `StockLot`, `InventoryMovement`, `ReportGenerator`, `PanyaException`, `BaseService`, `BaseController` | see above |
| interface `io/CsvRowMapper<T>` | product, ingredient and supplier row mappers in `CsvService` |
| functional interfaces `DataOperation<T>`, `ProgressListener`, `UiAction` | lambdas passed to the data store, backups and dialogs |

## 8. String

| Feature | Where |
|---|---|
| Case-insensitive search | `TextUtil.containsIgnoreCase` (products, ingredients, POS, sales history) |
| SKU validation (regex) and normalization | `TextUtil.SKU_PATTERN`, `normalizeCode`, `isValidSku` |
| SKU suggestion | `TextUtil.suggestSku` ("Japanese Milk Bread" → `BRE-JMB`) |
| Input normalization | `TextUtil.normalize` (trim, collapse spaces) |
| Number parsing with regex | `util/NumberParser` ("18.000" = 18000, "1,5" = 1.5) |
| Receipt layout (`StringBuilder`, padding, centering) | `io/ReceiptFormatter` |
| CSV quoting/parsing | `io/CsvWriter.escape`, `io/CsvReader` |
| Document numbers | `BakeryData.nextNumber` → `String.format("%s-%04d")` → `S-20261104-0001` |

## 9. Generics & Collections

| | Where |
|---|---|
| `List<T>` / `ArrayList` | every collection in `BakeryData` (`List<Product>`, `List<Sale>`, `List<InventoryMovement>`) |
| `Map` / `HashMap` / `TreeMap` / `LinkedHashMap` / `EnumMap` | `BakeryData.sequences` (`TreeMap`), `Recipe.scaleTo` (`LinkedHashMap<Long, BigDecimal>`), `DataValidator` (`HashMap` ledger balances), `Cart` (`LinkedHashMap<Long, CartLine>`), `PaymentMethodReport` / `WasteReport` (`EnumMap`) |
| `Set` / `HashSet` / `EnumSet` | `DataValidator` uniqueness checks, `Role` permissions (`EnumSet`) |
| Generic class | `FefoAllocator<T extends StockLot>` and `Allocation<T>` - one allocator for ingredient lots (production) and finished-goods batches (POS) |
| Generic methods | `BakeryData.findById`, `require`, `indexById` (`<T extends BaseEntity>`); `DataValidator.checkUniqueIds`; `UiUtil.column` |
| Generic interfaces | `DataOperation<T>`, `CsvRowMapper<T>`, `ImportPreview<T>` |

## 10. Exceptions & Assertions

- Checked hierarchy in `exception/`; services declare `throws BusinessException, PersistenceException`.
- **Rollback:** `DataStore.execute` catches any exception, rebuilds memory from the last saved JSON
  and rethrows - nothing partial is ever saved (`PersistenceTest.failedSaveRollsBackMemoryAndKeepsTheFile`).
- `InsufficientStockException` carries the full shortage list, shown in a dialog.
- `Dialogs.error` maps exception categories to user messages; unexpected errors go to the log with
  a reference code, never as a stack trace.
- `try-with-resources` in every IO class.
- **Assertions** (internal invariants, enabled in tests via Surefire `enableAssertions`):
  `StockLot.decrease` (`remaining >= 0`), `FefoAllocator.allocate` (allocations add up).
  User input is validated with exceptions, never with `assert`.

## 11. Java IO

| API | Where | Purpose |
|---|---|---|
| `FileChannel`, `ByteBuffer`, `force(true)` | `FileDataStorage.writeTempFile` | write the temp file and flush it to disk |
| `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` with retry | `FileDataStorage.moveWithRetry` | the commit point of every save |
| `Files.copy`, `deleteIfExists`, `readAllBytes`, `size` | `FileDataStorage` | previous generation, cleanup, reading |
| Jackson `ObjectMapper` over bytes | `JsonCodec` | JSON encode/decode with strict rules |
| `FileChannel.tryLock` | `InstanceLock` | only one app instance per data folder |
| `ZipOutputStream`, `ZipFile`, `MessageDigest` | `BackupService`, `Checksums` | backups with SHA-256 manifest and verification |
| `DirectoryStream` | `BackupService.listBackups`, `StartupInspector` | backup list, retention |
| `BufferedWriter` / `OutputStreamWriter` (UTF-8 + BOM) | `CsvWriter`, `ReceiptFormatter.save` | CSV export, receipt text files |
| `BufferedReader` / `InputStreamReader` | `CsvReader.read` | CSV import |
| `getResourceAsStream` | `SeedService` | starter recipe templates |
| `java.util.logging.FileHandler` | `AppLog` | rotating log files |

**Demo:** create a product, open `%LOCALAPPDATA%\PanyaYa\data\pan-ya-ya.json` in Notepad; make a
backup and open the `.panyabak` with 7-Zip to see `manifest.json`; truncate the JSON file and restart
→ recovery screen.

## 12. Java GUI

16 FXML screens in `src/main/resources/app/panyaya/ui/` with controllers in `ui/`, one CSS theme
(`ui/css/panyaya.css`), JavaFX `BarChart` (dashboard, reports), `TableView`, `TabPane`,
`SplitPane`, dialogs (`ui/common/FormDialog`, `Dialogs`). Controllers only call services;
business rules and file access are not in the UI.

## 13. Multithreading

| Background work | Where |
|---|---|
| Loading + validating the data file at start-up | `PanyaApp.start` → `TaskRunner.run` |
| Password hashing at sign-in (slow on purpose) | `LoginController.onSignIn` |
| Dashboard and report computation on a **copy** of the saved data | `DashboardController.refresh`, `ReportsController.onGenerate` → `DataStore.readCommittedCopy` |
| Backup / restore with a progress bar | `BackupController` → `TaskRunner.runWithProgress` (`Task.updateProgress`) |
| Daily automatic backup after sign-in | `MainController.startDailyBackup` |
| CSV export and import dry run | `SettingsController`, `ReportsController`, `InventoryController` |

Tools: `ExecutorService` (fixed pool of 2 named daemon threads in `AppContext`),
`javafx.concurrent.Task`, `Platform.runLater`. Thread safety: `DataStore.execute` is `synchronized`
(one change at a time); background threads read the immutable `volatile byte[] committedBytes`
snapshot instead of live objects. Proven by `PersistenceTest.concurrentOperationsAreSerialised`
(4 threads × 10 saves, no lost update).
