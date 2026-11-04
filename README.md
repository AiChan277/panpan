# Pan-ya Ya (パン屋ヤ)

**From Ingredients to Sales.** A JavaFX desktop application that runs the daily operations of a small
bakery: products, recipes, ingredients, stock lots, suppliers and deliveries, production, point of
sale, waste, reports, dashboard, users, backup and restore.

*Projek Akhir PBO - sistem manajemen toko roti.* Built for the Java OOP course; every course topic
is mapped to real code in [COURSE_COVERAGE.md](COURSE_COVERAGE.md).

## Features

| Module | What it does |
|---|---|
| Dashboard | Today's revenue, transactions, gross profit, production, waste, stock value; 14-day revenue chart; waste-by-reason chart; low-stock and expiry alerts; production vs. sales |
| Products | Create/edit/archive products (any name, e.g. "Japanese Milk Bread"), SKU validation and suggestion, categories, price history |
| Recipes | Recipe editor with ingredient lines and live cost estimate, versioning, duplicate, activate, scale calculator, 4 starter templates (as data) |
| Ingredients | Master data, stock unit, minimum stock, expiry tracking, opening stock, lots per ingredient |
| Inventory & Receiving | Goods receipts (several lines, unit conversion kg→g), lots and finished-goods batches, owner stock adjustments with reason, full stock ledger, CSV export |
| Suppliers | Supplier records and delivery history |
| Production | Plan → Start (ingredients consumed FEFO, refused with a shortage list) → Complete (good/rejected) or Fail; cancel |
| Point of Sale | Product tiles, search/scan by SKU, cart, F2/F12/Del/± shortcuts, cash/QRIS/card/transfer, change, receipt (save as .txt) |
| Sales History | Filters, receipt view, same-day void by the Owner (stock goes back) |
| Waste | Waste of batches or lots with loss = qty × lot cost, end-of-day write-off, history |
| Reports | 9 reports (daily, monthly, product performance, payment methods, ingredient inventory, finished goods, production, waste, ingredient usage) with chart and CSV export |
| Users | Owner / Baker / Cashier accounts, PBKDF2 password hashes, lockout after 5 failures |
| Settings | Bakery profile, thresholds, CSV import with dry run, CSV exports, integrity check, About |
| Backup & Restore | Verified `.panyabak` ZIP backups (SHA-256 manifest), daily automatic backups, validated restore |

All data is stored in **one JSON file** (`pan-ya-ya.json`), written atomically. No SQL or database is used.

## Technology

| | |
|---|---|
| Language | Java 25 (LTS) |
| GUI | JavaFX 25.0.5, FXML, CSS |
| Build | Maven 3.9 |
| JSON | Jackson 2.21 (LTS line) - the only third-party runtime library |
| Files | `java.io`, `java.nio.file`, `java.util.zip` |
| Tests | JUnit 5.14 (46 tests) |
| Packaging | jlink + jpackage + WiX Toolset 5 → Windows installer EXE |

## Prerequisites (developers)

- JDK 25 (e.g. Eclipse Temurin 25), Maven 3.9+.
- For packaging only: JavaFX 25 **jmods** for Windows (Gluon), WiX Toolset 5
  (`dotnet tool install wix --version 5.0.2 --tool-path %USERPROFILE%\tools\wix`, then
  `wix extension add -g WixToolset.UI.wixext/5.0.2` and `WixToolset.Util.wixext/5.0.2`).

End users need nothing: the installer contains its own Java runtime.

## Run (development)

```bash
mvn javafx:run
```

Data goes to `%LOCALAPPDATA%\PanyaYa`. To use a separate folder (e.g. demo data) set
`PANYAYA_HOME` or pass `-Dpanyaya.home=...`.

Demo data for presentations (accounts `owner/owner-demo-1`, `baker/baker-demo-1`,
`cashier/cashier-demo-1`):

```bash
mvn -q test-compile && java -cp "target/classes;target/test-classes;target/libs/*" app.panyaya.DemoData local-data
```

## Build and test

```bash
mvn clean package
```

## Package the Windows EXE

```bash
powershell -ExecutionPolicy Bypass -File build-windows.ps1
```

The script checks the tools, runs `mvn clean package` (all tests), builds a minimal runtime with
jlink (JDK modules from jdeps + JavaFX jmods), creates the app image, runs `--self-test` with the
bundled runtime, builds the installer and writes `SHA256SUMS.txt`. Tool locations are parameters
(`-JdkHome`, `-JavafxJmods`, `-MavenHome`, `-WixDir`). Output in `target\dist`:

| File | What it is |
|---|---|
| `Pan-ya Ya-1.0.0.exe` | Installer (Start menu + desktop shortcut, fixed upgrade code) |
| `Pan-ya Ya\Pan-ya Ya.exe` | Application launcher with bundled runtime |
| `Pan-ya Ya-1.0.0-portable.zip` | Portable version (unzip and run) |

Data lives in `%LOCALAPPDATA%\PanyaYa`, outside Program Files: updating or uninstalling never
deletes it.

## Project layout

```
src/main/java/app/panyaya
  model/         business objects (Product, Recipe, StockLot, InventoryMovement, Sale, ...)
  exception/     checked exception hierarchy
  persistence/   JSON file storage, atomic writes, validation, start-up recovery
  service/       one service per module (business rules, permissions)
  report/        ReportGenerator + 9 reports
  io/            CSV reader/writer, receipt formatter, checksums
  util/          strings, number parsing, formatting, logging
  ui/            FXML controllers and dialogs; ui/common = shared UI helpers
src/main/resources/app/panyaya
  ui/*.fxml, ui/css/panyaya.css, ui/img, data/starter-recipes.json
src/test/java    JUnit tests, DemoData, ScreenshotTour
```

More: [DEVELOPMENT_NOTES.md](DEVELOPMENT_NOTES.md) (architecture and simplifications),
[TESTING.md](TESTING.md) (test cases and results).
