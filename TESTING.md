# Testing

Run all tests: `mvn test` (JUnit 5, assertions enabled). Tests use real JSON files in a temporary
folder and a controllable clock (`MutableClock`), via the fixture `TestBakery`.

**Result (4 Nov 2026): 46 tests, 0 failures, 0 errors.**

## Unit and service tests

| Test class | What is verified |
|---|---|
| `ProductServiceTest` (5) | product creation + persistence after restart ("Japanese Milk Bread"); duplicate SKU (case-insensitive) refused with file byte-identical; invalid input; price history; archive; SKU suggestion |
| `InventoryTest` (5) | 25 kg for Rp 312.500 → lot 25,000 g at Rp 12,50/g + one receipt movement; expiry required; cross-dimension unit refused; stock never negative; low/out status; FEFO order and expired lots skipped |
| `RecipeAndProductionTest` (8) | scaling ×4 and ×1.5; incomplete cost estimate when cost unknown; exact cost per piece; activation validation; template requires review; start refused with full shortage list and nothing changed; FEFO consumption across two butter lots; complete 45 good / 3 rejected → batch, waste, unit cost; illegal transitions; failed batch loss; editing a recipe leaves history unchanged |
| `SalesAndWasteTest` (8) | totals, change, COGS, gross profit, receipt text; cash short → nothing saved; overselling refused (file unchanged); expired bread never sold (expiry == now counts as expired); void restores stock, same-day only, Owner only; cashier cannot create products; waste loss = qty × cost; waste rules (≤ remaining, OTHER note, whole pieces); end-of-day write-off in one save |
| `TextAndNumberTest` (5) | SKU rules and search; money parsing ("18.000", "Rp 1.500.000", ambiguous "1.5" refused); quantity parsing; exact BigDecimal arithmetic; unit conversion |

## Persistence tests

| Test | What is verified |
|---|---|
| `PersistenceTest.jsonRoundTrip...` | encode(decode(x)) == x; movement subclasses restored from `"type"`; decimals stored as strings; calculated getters not stored |
| `atomicSaveKeepsPreviousGeneration...` | `.prev.json` = file before the save; no `.tmp` left |
| `failedSaveRollsBackMemoryAndKeepsTheFile` | simulated disk-full: error says nothing was saved, memory rolled back, file byte-identical, rolled-back id not consumed |
| `truncatedFileGoesToQuarantine...` | damaged file → recovery state, file kept in quarantine, recovery from previous generation |
| `handEditedFileWithBrokenRulesIsRejectedOnLoad` | duplicate SKU edited into the file is reported by exact rule |
| `strictReadingRejectsUnknownPropertiesAndNewerVersions` | unknown property, newer schema, wrong format, empty file |
| `secondInstanceCannotLockTheSameFolder` | instance lock |
| `concurrentOperationsAreSerialised` | 4 threads × 10 saves → 40 products after reopening |
| `BackupAndCsvTest` (6) | backup → change → restore = backup state, pre-restore backup created, survives restart; tampered checksum and non-ZIP refused before any change; daily backup once per day; CSV quotes/BOM/semicolons/line breaks; CSV import dry run reports line + column, writes nothing, valid file imports all-or-nothing; export readable with BOM |

## Business workflow test

`WorkflowTest.ingredientsToSalesToReports` - create ingredients, product and recipe → receive
goods → cost estimate Rp 7.900/loaf → plan 10, start, complete 9 + 1 rejected → sell 3 (cash) and 2
(QRIS) → void the QRIS sale → record 1 damaged → **restart from disk** → dashboard and the daily
sales, waste, production, ingredient usage and product performance reports equal the hand-computed
values (revenue Rp 75.000, COGS Rp 23.700, gross profit Rp 51.300, waste Rp 15.800) → full integrity
check passes.

## UI check

`ScreenshotTour` (test sources) opens all 14 main screens plus sign-in and setup on the demo data
and saves PNG snapshots to `target/screenshots`. All FXML files load and all controllers initialise
without errors. Issues found this way and fixed: chart labels invisible in scroll panes (CSS),
report date column alignment, US date format in date pickers.

## Packaging tests

| Check | Result |
|---|---|
| `build-windows.ps1` end to end (tests, jlink, app image, installer, checksums) | passed |
| `--self-test` with the **bundled** runtime (create bakery, save product, reopen, money format) | `OK Pan-ya Ya 1.0.0 self-test passed (Java 25.0.5)` |
| `Pan-ya Ya.exe` started with PATH = `C:\Windows\System32;C:\Windows` and no JAVA_HOME | window "Pan-ya Ya - Bakery Operations" opened |
| Installer EXE built with WiX 5.0.2 | `Pan-ya Ya-1.0.0.exe` created |
| Install / upgrade / uninstall on a clean Windows VM (no JDK) | **not yet run** - do this before submission (Windows Sandbox works) |

### Manual clean-machine checklist (to do)

1. Windows Sandbox or fresh VM without Java → run `Pan-ya Ya-1.0.0.exe`.
2. Setup wizard → Workflows: product + recipe → receive → produce → sell → waste → reports.
3. Kill the app during use (Task Manager) → restart → data valid.
4. Install a rebuilt 1.0.1 over it → data kept; uninstall → `%LOCALAPPDATA%\PanyaYa` still present.
