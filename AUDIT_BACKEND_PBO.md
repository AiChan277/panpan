# Audit Backend - Prasyarat Mata Kuliah PBO (Java)

Audit backend Pan-ya Ya terhadap 14 pokok bahasan mata kuliah: OOP Introduction, Class & Object,
Java Variables, Inheritance, Encapsulation, Polymorphism, Abstraction, UTS, String,
Generics & Collections, Exceptions & Assertions, Java IO, Java GUI, Multithreading.

> **Status dokumen:** rekomendasi saja. Pada pekerjaan redesign UI, **tidak ada satu baris pun**
> di `model/`, `service/`, `persistence/`, `report/`, `io/`, `exception/`, `util/` atau di test
> yang diubah. Semua path di bawah relatif terhadap `src/main/java/app/panyaya/`.

## Ringkasan

| # | Topik | Status | Celah utama | Prioritas |
|---|---|---|---|---|
| 1 | OOP Introduction | Kuat | Belum ada diagram kelas untuk presentasi | P3 |
| 2 | Class & Object | Kuat | Belum memakai `record`; `toString()` domain jarang | P2 |
| 3 | Java Variables | Kuat | - | - |
| 4 | Inheritance | Kuat | - | - |
| 5 | Encapsulation | Cukup | `BakeryData` mengembalikan `List` yang bisa diubah langsung | P2 |
| 6 | Polymorphism | Kuat | Overloading (compile-time) belum didokumentasikan | P3 |
| 7 | Abstraction | Kuat | Belum ada `default` method / `sealed` hierarchy | P2 |
| 8 | UTS | - | Persiapan demo materi 1-7 | P3 |
| 9 | String | Kuat | - | P3 |
| 10 | Generics & Collections | Cukup | Tidak ada wildcard (`? extends`/`? super`), `Comparable`, `Iterator`/`Iterable`, `Queue` | **P1** |
| 11 | Exceptions & Assertions | Cukup | Hanya 2 `assert`, dan `-ea` tidak aktif saat aplikasi dijalankan | **P1** |
| 12 | Java IO | Kuat, satu celah | Tidak ada object serialization (`Serializable`, `ObjectOutputStream`) | **P1** |
| 13 | Java GUI | Kuat | (diperkuat oleh redesign ini) | - |
| 14 | Multithreading | Cukup | Tidak ada `ScheduledExecutorService` / `CompletableFuture`; thread hanya lewat pool | **P1** |

**P1** = celah yang mungkin ditanyakan dosen langsung karena merupakan materi inti; kecil dan aman
untuk ditambahkan. **P2** = memperkuat nilai desain. **P3** = dokumentasi/presentasi.

## Cara audit dilakukan

1. Membaca `COURSE_COVERAGE.md` dan memverifikasi setiap klaimnya di kode.
2. Menghitung pemakaian fitur bahasa di paket backend (`model service persistence report io exception util`):

| Fitur | Jumlah | Fitur | Jumlah |
|---|---|---|---|
| `abstract class` | 7 | wildcard `? extends`/`? super` | **0** |
| `interface` | 4 | `implements Comparable` | **0** |
| `default` method di interface | **0** | `Iterator` / `Iterable` | **0** |
| `@Override` | 96 | `Queue` / `Deque` | **0** |
| `super(...)` | 53 | Stream API (`.stream()`) | **0** |
| `enum` | 15 | `assert` | **2** |
| `synchronized` / `volatile` | 7 / 2 | `Serializable` / `ObjectOutputStream` | **0 / 0** |
| try-with-resources | 10 | `ScheduledExecutorService` / `CompletableFuture` | **0 / 0** |
| `throws` | 166 | `record` / `sealed` | **0 / 0** |

3. Menjalankan seluruh test: **46 test, 0 gagal** (`mvn clean package`).

---

## 1. OOP Introduction - Kuat

**Sudah ada:** domain bakery dimodelkan sebagai objek (`Product`, `Ingredient`, `Recipe`,
`IngredientLot`, `FinishedGoodsBatch`, `ProductionOrder`, `Sale`, `WasteRecord`, `Supplier`,
`User`) dan lapisan yang jelas: `ui/` -> `service/` -> `model/` -> `persistence/`.

**Rekomendasi (P3):** buat 3 diagram untuk presentasi (PlantUML atau draw.io, simpan di `docs/`):
diagram kelas `model/inventory` (hierarki `StockLot` dan `InventoryMovement`), diagram hierarki
`exception/`, dan sequence diagram checkout (`PosController.onPay` -> `SalesService.checkout` ->
`FefoAllocator.allocate` -> `DataStore.execute`).

## 2. Class & Object - Kuat

**Sudah ada:** konstruktor dengan validasi (`model/product/Product.java`), objek sementara
(`model/sales/Cart.java`), `equals/hashCode` di `model/BaseEntity.java`, static nested class
(18), static factory (`Quantities.of`).

**Celah:** tidak ada `record`; sebagian besar kelas domain tidak meng-override `toString()`.

**Rekomendasi (P2):**
- Ubah *value object yang tidak disimpan ke JSON* menjadi `record`, misalnya
  `report/DateRange`, `service/MaterialRequirement`, `service/CostEstimate`,
  `DashboardSummary.Alert`. Contoh:
  ```java
  public record DateRange(LocalDate from, LocalDate to) {
      public DateRange {                       // compact constructor = validasi
          if (to.isBefore(from)) throw new IllegalArgumentException("to < from");
      }
  }
  ```
  Ini menunjukkan perbedaan *class biasa* (mutable, identitas) vs *record* (immutable, nilai).
  Jangan ubah entity yang disimpan Jackson (`Product`, `Sale`, ...) tanpa test persistence.
- Tambahkan `toString()` di `BaseEntity` (mis. `"Product#12"`), berguna saat debugging dan demo.

## 3. Java Variables - Kuat

Konstanta `static final` (`Money.SCALE`, `User.MAX_FAILED_ATTEMPTS`), variabel static
(`TextUtil.SKU_PATTERN`), instance, lokal, primitif vs referensi, dan `BigDecimal` untuk uang
(`TextAndNumberTest` membuktikan 0.1 + 0.2 = 0.3000). Tidak ada celah. Opsional: satu contoh `var`
dan contoh autoboxing (`int` vs `Integer`) di materi presentasi.

## 4. Inheritance - Kuat

Hierarki nyata dan beralasan: `BaseEntity`, `StockLot` <- `IngredientLot`/`FinishedGoodsBatch`,
`InventoryMovement` <- 7 subclass, `ReportGenerator` <- 9 laporan, hierarki exception,
`BaseService`, `BaseController`. Keputusan "role sebagai data, bukan subclass `Employee`" sudah
didokumentasikan - jawaban bagus untuk pertanyaan "kenapa tidak pakai inheritance di sini?".

**Tambahan dari redesign UI** (bisa dipakai sebagai contoh inheritance di lapisan GUI):
`ui/common/Icon extends StackPane`, `CardGrid extends Pane` (override `layoutChildren`,
`computePrefHeight`), `KpiCard extends VBox`, `Badge extends Label`.

## 5. Encapsulation - Cukup

**Sudah ada:** semua field `private`; `Product.changePrice()` alih-alih `setPrice`;
`StockLot.increase()/decrease()` package-private sehingga stok hanya berubah lewat ledger;
transisi status `ProductionOrder` dijaga; `Recipe.getItems()` mengembalikan
`Collections.unmodifiableList`.

**Celah:** `model/BakeryData.java` mengembalikan list internal apa adanya:
```java
public List<Product> getProducts() {
    return products;            // siapa pun bisa products.clear()
}
```
Saat ini aman karena perubahan data hanya terjadi di dalam `DataStore.execute` (yang
`synchronized` dan punya rollback), tetapi secara teori encapsulation ini "bocor" dan mudah
ditanyakan dosen.

**Rekomendasi (P2), pilih salah satu:**
- Kembalikan view read-only untuk pembaca, dan beri method mutasi yang eksplisit:
  ```java
  public List<Product> getProducts() { return Collections.unmodifiableList(products); }
  void addProduct(Product p) { products.add(p); }   // package-private / dipanggil service
  ```
  Perlu menyesuaikan service yang saat ini memanggil `getProducts().add(...)` dan memastikan
  Jackson tetap memakai field (cek `JsonCodec`), lalu jalankan semua test.
- Atau, jika tidak ingin mengubah: dokumentasikan sebagai *aggregate root* yang hanya boleh
  diubah di dalam `DataStore.execute`, dan tunjukkan buktinya (`PersistenceTest`).

## 6. Polymorphism - Kuat

**Sudah ada (runtime / overriding):** `InventoryMovement.applyTo()` dengan `isIncoming()` dan
`checkAllowed()` yang dioverride; `StockLot.isExpiredAt()` (tanggal vs instant);
`ReportGenerator.generate()` memanggil `fill()` milik masing-masing laporan; JSON `"type"`
memetakan kembali ke subclass.

**Rekomendasi (P3):** sebutkan juga *compile-time polymorphism* (overloading) yang sudah ada,
misalnya `util/Formatters.quantity(BigDecimal)` vs `quantity(BigDecimal, Unit)`, supaya kedua
jenis polymorphism terlihat di presentasi.

## 7. Abstraction - Kuat

**Sudah ada:** interface `persistence/DataStorage` (implementasi file + implementasi test),
`io/CsvRowMapper<T>`, functional interface `DataOperation<T>`, `ProgressListener`; abstract class
`StockLot`, `InventoryMovement`, `ReportGenerator`, `PanyaException`, `BaseService`.
`ReportGenerator.generate()` adalah **Template Method** (`final` + hook `abstract getColumns()` dan
`fill()`) - pola yang bagus untuk dijelaskan.

**Rekomendasi (P2):**
- Tambahkan satu `default` method agar perbedaan interface vs abstract class terlihat, contoh di
  `io/CsvRowMapper`:
  ```java
  default List<T> mapAll(List<CsvReader.Row> rows, List<ImportError> errors) {
      List<T> result = new ArrayList<>();
      for (CsvReader.Row row : rows) {
          T item = map(row, errors);
          if (item != null) result.add(item);
      }
      return result;
  }
  ```
- (Opsional, Java 17+) jadikan `InventoryMovement` `sealed ... permits StockReceipt,
  ProductionConsumption, ProductionOutput, SaleIssue, SaleReversal, WasteWriteOff,
  StockAdjustment`. Ini menunjukkan abstraksi yang hierarkinya tertutup dan aman. Pastikan
  `InventoryMovementMixin` (Jackson) tetap bekerja.

## 8. UTS - persiapan

UTS biasanya mencakup topik 1-7. Rekomendasi (P3): siapkan skrip demo 10 menit:
1. Buat produk "Japanese Milk Bread" (Class & Object, validasi di konstruktor).
2. Terima bahan (Receive goods) -> lot baru (inheritance `StockLot`).
3. Plan -> Start produksi dengan stok kurang -> ditolak (`InsufficientStockException`), stok tetap
   (encapsulation + exception).
4. Jual di POS -> FEFO memilih batch paling cepat kedaluwarsa (polymorphism `isExpiredAt`).
5. Buka Reports -> `ReportGenerator` (abstraction + template method).

## 9. String - Kuat

Regex SKU (`TextUtil.SKU_PATTERN`), normalisasi input, saran SKU, parsing angka Indonesia
(`util/NumberParser`), `StringBuilder` untuk struk (`io/ReceiptFormatter`), escape/parsing CSV,
`String.format("%s-%04d")` untuk nomor dokumen. Tidak ada celah berarti. Opsional: satu contoh
perbandingan `==` vs `equals` dan immutability String di materi presentasi.

## 10. Generics & Collections - Cukup (P1)

**Sudah ada:** kelas generik dengan bounded type `FefoAllocator<T extends StockLot>` dan
`Allocation<T>`; method generik `BakeryData.findById/require/indexById <T extends BaseEntity>`;
interface generik `DataOperation<T>`, `CsvRowMapper<T>`; `ArrayList`, `HashMap`, `TreeMap`,
`LinkedHashMap`, `EnumMap`, `HashSet`, `EnumSet`; `Comparator` (32 pemakaian).

**Celah:** materi standar yang sering diujikan belum muncul: wildcard, `Comparable`,
`Iterator`/`Iterable`, `Queue`/`Deque`.

**Rekomendasi (P1), kecil dan aman:**
1. **Wildcard (PECS).** Method yang hanya *membaca* list cukup menerima `? extends T`:
   ```java
   public static <T extends BaseEntity> T findById(List<? extends T> list, long id) { ... }
   public static <T extends BaseEntity> Map<Long, T> indexById(Collection<? extends T> list) { ... }
   ```
   dan di `FefoAllocator`: `usableLotsInOrder(List<? extends T> lots, ...)`. Signature lebih
   fleksibel, pemanggil tidak perlu diubah.
2. **`Comparable`.** Jadikan urutan FEFO sebagai *natural ordering* lot, sehingga ada contoh
   `Comparable` (urutan alami) dan `Comparator` (urutan alternatif) berdampingan:
   ```java
   public abstract class StockLot extends BaseEntity implements Comparable<StockLot> {
       @Override
       public int compareTo(StockLot other) { /* expiry naik, lalu receivedAt, lalu id */ }
   }
   ```
   Hati-hati: `FinishedGoodsBatch` membandingkan `Instant`, `IngredientLot` membandingkan
   `LocalDate` + zona waktu; logika di `FefoAllocator.fefoOrder(zone)` saat ini butuh `zone`, jadi
   `compareTo` sebaiknya hanya dipakai untuk urutan yang tidak bergantung zona (mis. `receivedAt`,
   lalu `id`), dan `fefoOrder` tetap sebagai `Comparator`.
3. **`Queue`.** `FefoAllocator.allocate` bisa memakai `PriorityQueue<T>` dengan comparator FEFO
   (ambil lot "paling mendesak" dengan `poll()`), contoh nyata struktur antrean.
4. **`Iterable`.** `Recipe implements Iterable<RecipeItem>` atau `Cart implements
   Iterable<CartLine>` agar bisa `for (CartLine line : cart)`; tunjukkan `Iterator` +
   `remove()` aman saat iterasi.
5. (Opsional) satu contoh Stream API di laporan, mis. `WasteReport` dengan
   `Collectors.groupingBy(WasteRecord::getReason, () -> new EnumMap<>(WasteReason.class), ...)`.

Setiap perubahan ditutup dengan test di `InventoryTest` / `RecipeAndProductionTest`.

## 11. Exceptions & Assertions - Cukup (P1)

**Sudah ada:** hierarki checked exception (`PanyaException` -> `BusinessException` /
`PersistenceException` -> turunannya); rollback di `DataStore.execute`; `InsufficientStockException`
membawa daftar kekurangan; try-with-resources (10); multi-catch (3); pesan ramah di UI dan
nomor referensi untuk error tak terduga.

**Celah:**
- Hanya **2** `assert` (`StockLot.decrease`, `FefoAllocator.allocate`).
- Assertion hanya aktif saat test (Surefire `enableAssertions`); `run-dev.bat` / `mvn javafx:run`
  menjalankan aplikasi **tanpa** `-ea`, jadi saat demo assertion tidak pernah dievaluasi.
- `finally` hanya dipakai sekali (wajar karena try-with-resources), tapi tidak ada contoh yang bisa
  ditunjukkan untuk materi `finally`.

**Rekomendasi (P1):**
1. Tambahkan assertion untuk invariant internal (bukan validasi input user), misalnya:
   ```java
   // Sale.complete()  - sumOfLines() adalah method bantu kecil yang perlu ditambahkan
   assert total.compareTo(sumOfLines()) == 0 : "sale total must equal its lines";
   // ProductionOrder.complete(...)
   assert goodQuantity >= 0 && rejectedQuantity >= 0;
   // Money: hasil pembulatan selalu SCALE desimal
   assert result.scale() == SCALE;
   ```
2. Aktifkan `-ea` untuk mode pengembangan di `pom.xml` (plugin `javafx-maven-plugin`):
   ```xml
   <configuration>
       <mainClass>${main.class}</mainClass>
       <options><option>-ea</option></options>
   </configuration>
   ```
   (Installer produksi tetap tanpa `-ea`, sesuai praktik umum.)
3. Satu contoh `finally` yang bermakna, mis. membuka/menutup indikator sibuk atau melepas lock
   di `InstanceLock`.

## 12. Java IO - Kuat, satu celah (P1)

**Sudah ada (sangat lengkap):** `FileChannel` + `force(true)`, atomic move dengan retry
(`persistence/FileDataStorage`), `FileChannel.tryLock` (`InstanceLock`), ZIP + SHA-256
(`BackupService`, `io/Checksums`), `BufferedReader`/`BufferedWriter` untuk CSV dan struk,
`getResourceAsStream` (`SeedService`), logging ke file (`util/AppLog`).

**Celah:** materi Java IO umumnya juga mencakup **object serialization** (`Serializable`,
`ObjectOutputStream`, `ObjectInputStream`, `transient`), dan itu belum ada. Data utama memang
lebih tepat di JSON (bisa dibaca manusia, aman terhadap perubahan kelas) - jangan diganti.

**Rekomendasi (P1):** gunakan serialization untuk data kecil yang **tidak kritis**, misalnya
preferensi tampilan per komputer (yang sekarang relevan karena sidebar baru bisa di-collapse):
```java
public final class UiPreferences implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean sidebarCollapsed;
    private String lastUsername;
    private String lastReport;
    private transient Instant loadedAt;   // tidak ikut disimpan
}
// simpan
try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(paths.getHome().resolve("ui.prefs")))) {
    out.writeObject(prefs);
}
// baca (jika file rusak/versi beda: abaikan dan pakai default)
try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
    prefs = (UiPreferences) in.readObject();
} catch (IOException | ClassNotFoundException e) { prefs = new UiPreferences(); }
```
Ini sekaligus menjadi bahan diskusi: *kenapa data bisnis memakai JSON tetapi preferensi memakai
serialization* (keamanan deserialisasi, kompatibilitas versi, keterbacaan).

Opsional: satu contoh `PrintWriter` (mis. `ReceiptFormatter.save` dengan `printf`) dan penjelasan
bahwa `java.nio.file.Path/Files` adalah pengganti modern `java.io.File`.

## 13. Java GUI - Kuat

17 FXML + controller, satu stylesheet, `TableView`, `TabPane`, `SplitPane`, `BarChart`, dialog.
Redesign ini menambahkan bahan yang bisa dipresentasikan:
- **Komponen reusable** di `ui/common/`: `Icon` (memuat SVG dari classpath, bisa dipakai di FXML
  `<Icon name="search"/>`), `CardGrid` (layout responsif buatan sendiri), `KpiCard`, `Badge`,
  `EmptyState`.
- **Layout responsif:** sidebar otomatis menjadi ikon di bawah 1240 px, toolbar otomatis
  menampilkan ikon saja saat sempit, kolom tabel memakai constrained resize policy.
- **CSS design system:** warna sebagai looked-up color di `.root`, pseudo-class (`:today` pada bar
  chart, `:focus-visible`, `:focus-within`).
- **Event handling & binding:** `ToggleGroup` navigasi, listener lebar window, binding lebar bar
  limbah (`fill.prefWidthProperty().bind(...)`).

## 14. Multithreading - Cukup (P1)

**Sudah ada:** `ExecutorService` (pool 2 thread daemon bernama, `AppContext`) dengan
`ThreadFactory` + `AtomicInteger`; `javafx.concurrent.Task`; `Platform.runLater`;
`DataStore.execute` `synchronized`; snapshot `volatile byte[]` untuk pembaca latar belakang;
`PersistenceTest.concurrentOperationsAreSerialised` (4 thread x 10 save, tidak ada update hilang).

**Celah:** tidak ada pekerjaan berkala (`ScheduledExecutorService`), tidak ada komposisi async
(`CompletableFuture`), dan tidak ada contoh eksplisit `Thread`/`Runnable` yang mudah ditunjukkan.

**Rekomendasi (P1):**
1. **`ScheduledExecutorService`** untuk tugas berkala yang memang berguna:
   ```java
   ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
           new NamedDaemonThreadFactory("panyaya-scheduler"));
   scheduler.scheduleAtFixedRate(() -> {
       try { backups().createDailyBackupIfNeeded(); }      // sudah idempoten per hari
       catch (Exception e) { AppLog.error("Scheduled backup failed", e); }
   }, 1, 60, TimeUnit.MINUTES);
   ```
   Sekarang backup harian hanya dicek sekali setelah login; jika aplikasi dibiarkan menyala
   melewati tengah malam, backup hari berikutnya tidak dibuat. Jangan lupa `shutdownNow()` di
   `AppContext.shutdown()`.
2. **`CompletableFuture`** untuk menghitung beberapa laporan paralel di atas
   `DataStore.readCommittedCopy()` (sudah thread-safe):
   ```java
   CompletableFuture<Report> sales = CompletableFuture.supplyAsync(() -> daily.generate(copy, range, now, zone), executor);
   CompletableFuture<Report> waste = CompletableFuture.supplyAsync(() -> wasteReport.generate(copy, range, now, zone), executor);
   CompletableFuture.allOf(sales, waste).join();
   ```
3. Di presentasi, tunjukkan *kenapa* `synchronized` diperlukan: jalankan test konkurensi dengan
   `synchronized` dihapus sementara (akan gagal karena lost update), lalu kembalikan.

---

## Rencana kerja yang disarankan

| Urutan | Pekerjaan | File utama | Risiko | Test |
|---|---|---|---|---|
| 1 | Wildcard + `Comparable` + `PriorityQueue` + `Iterable` | `model/BakeryData`, `model/inventory/FefoAllocator`, `StockLot`, `Cart` | Rendah | `InventoryTest`, `SalesAndWasteTest` |
| 2 | Assertion invariant + `-ea` saat dev | `Sale`, `ProductionOrder`, `Money`, `pom.xml` | Rendah | semua test (Surefire sudah `-ea`) |
| 3 | `UiPreferences` dengan serialization | baru: `persistence/UiPreferencesStore` | Rendah (file terpisah, gagal = default) | test baca/tulis + file rusak |
| 4 | `ScheduledExecutorService` untuk backup berkala | `AppContext`, `MainController` | Sedang (shutdown thread) | test idempoten backup |
| 5 | `record` untuk value object non-persisten, `default` method | `report/DateRange`, `io/CsvRowMapper` | Rendah | `BackupAndCsvTest` |
| 6 | Encapsulation `BakeryData` | `model/BakeryData` + service | Sedang (banyak pemanggil, Jackson) | semua test |
| 7 | Diagram + skrip demo | `docs/` | - | - |

## Yang sebaiknya tidak diubah

- **Penyimpanan JSON.** Silabus tidak mensyaratkan database/SQL; penyimpanan file dengan
  `java.nio` + atomic write + backup ZIP justru menjadi contoh Java IO yang kuat. Jika dosen
  menanyakan pola DAO, `persistence/DataStorage` sudah berperan sebagai abstraksi akses data.
- **Uang dengan `BigDecimal`.** Jangan pernah diganti ke `double`.
- **Validasi input dengan exception, bukan `assert`.** Assertion bisa dimatikan; validasi tidak boleh.
