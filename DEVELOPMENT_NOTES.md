# Development notes

## Architecture

```
ui (FXML + controllers)  →  service (business rules, permissions)  →  model (entities)
                                     ↓
                             persistence.DataStore  →  DataStorage (FileDataStorage)  →  pan-ya-ya.json
```

- **AppContext** is the composition root: it creates every service once and hands them to the UI
  (manual dependency injection, no framework).
- **BakeryData** is the whole data set (lists of entities) and is exactly what is written to JSON.
- **DataStore.execute(name, operation)** is the single way to change data: run the operation on the
  in-memory data → `DataValidator` checks the whole data set → encode → atomic file write → only
  then remember the new JSON as committed. On any failure memory is rebuilt from the last committed
  JSON bytes, so memory and disk always agree.
- **FileDataStorage** writes `pan-ya-ya.json.tmp`, forces it to disk, copies the old file to
  `pan-ya-ya.prev.json`, then replaces the primary with an atomic move (retried if Windows holds the
  file briefly).
- **StartupInspector** never opens empty data by accident: missing/damaged file → quarantine +
  recovery screen (previous generation, backup, or a deliberately typed "NEW").
- Stock is only changed by **InventoryMovement** objects (append-only ledger). The validator checks
  that each lot's quantity equals the sum of its movements.

## Simplifications made from Technical Draft v0.2 (deliberate, course-aligned)

| v0.2 design | Implemented instead | Why |
|---|---|---|
| Immutable entities, `AppDataSnapshot`/`WorkingCopy`, copy-on-write, single writer thread with `CompletableFuture` | Ordinary encapsulated mutable entities; `synchronized DataStore.execute`; rollback by re-reading the last committed JSON bytes | Same all-or-nothing guarantee with far fewer concepts; explainable in a presentation |
| Sealed classes, pattern-matching `switch`, records | Abstract classes, overriding, `instanceof`; plain classes | Matches the syllabus |
| Repository interfaces per entity, `EntityCollection<T,ID>`, typed id records | Lists in `BakeryData` + generic helpers `findById/indexById`; `long` ids | Avoids a generic repository framework |
| 15 `IntegrityRule` classes | One `DataValidator` with private check methods | Same checks, less ceremony |
| Jackson 3.1 (`tools.jackson`) | Jackson 2.21 LTS (`com.fasterxml`) + JSR-310 module | Course tutorials use 2.x; codec isolated in `JsonCodec` + one mix-in |
| DTO record layer between file and domain | Jackson reads private fields directly (field visibility), mix-in for movement subtypes | One less mapping layer; model stays free of Jackson annotations |
| Recipe copy-on-write versions with content hash | One recipe object with a version counter; production orders copy the materials and costs actually used | History stays correct; simpler model |
| Purchase orders (DRAFT → ORDERED → RECEIVED) | Direct goods receipt (with supplier, invoice no., lots, expiry) | Covers the real workflow; ordering is a future feature |
| Period archiving, format migrators, hard-link previous generation | Not implemented; previous generation is a file copy; `schemaVersion` is checked (newer files refused) | Not needed at this data size (see limitations) |
| Indonesian UI via ResourceBundle | English UI; money shown in Indonesian format (Rp 18.000) | Time; strings are in one language for now |
| Unit table configurable | `Unit` enum (g, kg, mL, L, pcs, dozen) | Enum demonstrates fields/methods on constants |
| PLANNED order freezes the recipe version | Recipe is read at **Start**; the version used is recorded | Plans can be made before a recipe tweak |

### Business-rule choice that differs from v0.2 AC-06

Batch unit cost = material cost ÷ (good + rejected). The rejected pieces are recorded as
production-failure waste at that same unit cost, so good stock + waste add up exactly to the material
cost. (v0.2 divided by good only, which counts the rejected share twice.)

## Trade-offs

- Every save rewrites the whole JSON file. With hundreds of sales per day the file grows by roughly
  0.3-0.5 MB per day; saves stay fast for months, but see limitations.
- Business saves run on the JavaFX thread (they take milliseconds on small files and must finish
  before the UI shows "saved"). Slow work - start-up loading, password hashing, reports, dashboard,
  backups, restore, CSV - runs on background threads.
- Reports and the dashboard decode a copy of the last saved JSON, so they never see half-finished
  changes and never block sales.

## Known limitations (honest list)

- Not a database: no multi-PC access; one process per data folder (enforced by a file lock).
- After a sudden power loss right after a save, Windows may keep the previous file version
  (the newest sale could be lost); the previous generation and daily backups limit the damage.
- No period archiving: after many months the file becomes large and saves slower.
- No tax, discounts, split payments, refunds, purchase orders, stock-count sessions, sub-recipes,
  printer/cash-drawer support (P1 items in v0.2).
- English UI only; installer is unsigned (SmartScreen warning).
- The clean-machine install test (fresh Windows VM without Java) has **not** been run yet; the app
  image was verified to start with no Java on PATH and no JAVA_HOME (see TESTING.md).

## Future improvements

Period archiving; purchase orders and partial receipts; Indonesian ResourceBundle; tax and
discounts; refunds; thermal printer; stock opname; recipe diff; signed installer; CI build on a
Windows runner.

## Build environment used

Temurin JDK 25.0.5 (no jmods; jlink links JDK modules from the run-time image, JEP 493),
JavaFX 25.0.5 jmods (Gluon), Maven 3.9.16, WiX Toolset 5.0.2 as a local dotnet tool with the UI and
Util extensions. Library permission note: Jackson is the only third-party runtime library; whether
the lecturer allows it has not been confirmed. If it is not allowed, only `persistence/JsonCodec`
and `InventoryMovementMixin` need replacing.
