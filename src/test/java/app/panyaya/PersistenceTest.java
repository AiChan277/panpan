package app.panyaya;

import app.panyaya.exception.DataCorruptedException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.Money;
import app.panyaya.model.Unit;
import app.panyaya.model.inventory.Ingredient;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.inventory.StockReceipt;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductType;
import app.panyaya.persistence.AppPaths;
import app.panyaya.persistence.DataStorage;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.FileDataStorage;
import app.panyaya.persistence.InstanceLock;
import app.panyaya.persistence.JsonCodec;
import app.panyaya.persistence.StartupInspector;
import app.panyaya.service.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest {

    @TempDir
    Path dir;

    @Test
    void jsonRoundTripKeepsEverythingIncludingPolymorphicMovements() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        Ingredient flour = bakery.ingredient("FLOUR", "Flour", Unit.G, "1000", true);
        bakery.receive(flour, "2.5", Unit.KG, 31250, bakery.today().plusDays(10));
        JsonCodec codec = new JsonCodec();

        byte[] first = codec.encode(bakery.ctx.getStore().getData());
        BakeryData decoded = codec.decode(first);
        byte[] second = codec.encode(decoded);
        assertArrayEquals(first, second, "encode(decode(x)) == x");

        InventoryMovement movement = decoded.getMovements().get(0);
        assertTrue(movement instanceof StockReceipt, "subclass restored from the \"type\" property");
        String json = new String(first, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"type\" : \"STOCK_RECEIPT\""));
        assertTrue(json.contains("\"remainingQuantity\" : \"2500.000\""));
        assertFalse(json.contains("remainingValue"), "calculated getters are not stored");
    }

    @Test
    void atomicSaveKeepsPreviousGenerationAndLeavesNoTempFile() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        FileDataStorage storage = (FileDataStorage) bakery.ctx.getStorage();
        byte[] beforeSave = Files.readAllBytes(storage.getPrimary());

        bakery.manufactured("Roti Tawar", "RT-TAWAR", 18000, 48);

        assertTrue(Files.exists(storage.getPrevious()));
        assertArrayEquals(beforeSave, Files.readAllBytes(storage.getPrevious()), "prev = file before the save");
        assertFalse(Files.exists(storage.getTemp()));
        assertTrue(Files.readString(storage.getPrimary()).contains("RT-TAWAR"));
    }

    @Test
    void failedSaveRollsBackMemoryAndKeepsTheFile() throws Exception {
        // a storage that works for setup and then fails on demand (simulates disk full / file locked)
        FailingStorage storage = new FailingStorage(new FileDataStorage(dir.resolve("data/pan-ya-ya.json")));
        MutableClock clock = new MutableClock(TestBakery.START);
        AppContext ctx = new AppContext(new AppPaths(dir), storage, clock, new PasswordHasher(1000));
        ctx.auth().setupNewBakery("Test", "owner", "Owner", "owner-secret".toCharArray());
        long categoryId = ctx.products().getCategories(false).get(0).getId();
        byte[] fileBefore = Files.readAllBytes(dir.resolve("data/pan-ya-ya.json"));
        long savesBefore = ctx.getStore().getData().getSaveCount();

        storage.failNext = true;
        PersistenceException error = assertThrows(PersistenceException.class, () -> ctx.products().createProduct(
                "Croissant", "PAS-CROIS", categoryId, ProductType.MANUFACTURED, Money.of(15000), 24, ""));
        assertTrue(error.getMessage().contains("nothing was saved"));

        assertTrue(ctx.products().getActiveProducts().isEmpty(), "in-memory change was rolled back");
        assertEquals(savesBefore, ctx.getStore().getData().getSaveCount());
        assertArrayEquals(fileBefore, Files.readAllBytes(dir.resolve("data/pan-ya-ya.json")));

        // the next save works normally
        Product created = ctx.products().createProduct("Croissant", "PAS-CROIS", categoryId, ProductType.MANUFACTURED,
                Money.of(15000), 24, "");
        assertNotNull(created);
        assertEquals(1, created.getId(), "the rolled-back id was not consumed");
    }

    @Test
    void truncatedFileGoesToQuarantineAndRecoveryNeverStartsEmpty() throws Exception {
        // AC-15
        TestBakery bakery = new TestBakery(dir);
        bakery.manufactured("Roti Tawar", "RT-TAWAR", 18000, 48);
        Path dataFile = bakery.ctx.getPaths().getDataFile();
        byte[] content = Files.readAllBytes(dataFile);
        Files.write(dataFile, Arrays.copyOf(content, content.length / 2));

        AppContext restarted = new AppContext(new AppPaths(dir), bakery.clock, new PasswordHasher(1000));
        StartupInspector.Result result = restarted.getStartupInspector().inspect();
        assertEquals(StartupInspector.State.RECOVERY_NEEDED, result.getState());
        assertNotNull(result.getQuarantinedFile());
        assertTrue(Files.exists(result.getQuarantinedFile()), "damaged file kept, not deleted");
        assertFalse(restarted.getStore().isLoaded());

        // recover from the previous generation: loses at most the last save
        restarted.getStartupInspector().recoverFromPreviousGeneration();
        assertTrue(restarted.getStore().isLoaded());
        assertEquals(1, restarted.getStore().getData().getUsers().size());
    }

    @Test
    void handEditedFileWithBrokenRulesIsRejectedOnLoad() throws Exception {
        // AC-16: two products sharing a SKU
        TestBakery bakery = new TestBakery(dir);
        bakery.manufactured("Roti Tawar", "RT-TAWAR", 18000, 48);
        bakery.manufactured("Roti Manis", "RT-MANIS", 8000, 36);
        Path dataFile = bakery.ctx.getPaths().getDataFile();
        String json = Files.readString(dataFile).replace("\"RT-MANIS\"", "\"RT-TAWAR\"");
        Files.writeString(dataFile, json);

        DataStore store = new AppContext(new AppPaths(dir), bakery.clock, new PasswordHasher(1000)).getStore();
        DataCorruptedException error = assertThrows(DataCorruptedException.class, store::load);
        assertTrue(error.getProblems().contains("Duplicate product SKU \"RT-TAWAR\""));
    }

    @Test
    void strictReadingRejectsUnknownPropertiesAndNewerVersions() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        JsonCodec codec = new JsonCodec();
        String json = Files.readString(bakery.ctx.getPaths().getDataFile());

        String unknown = json.replaceFirst("\\{", "{ \"hacked\" : true,");
        assertThrows(DataCorruptedException.class, () -> codec.decode(unknown.getBytes(StandardCharsets.UTF_8)));

        String newer = json.replace("\"schemaVersion\" : 1", "\"schemaVersion\" : 99");
        DataCorruptedException error = assertThrows(DataCorruptedException.class,
                () -> codec.decode(newer.getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("newer"));

        assertThrows(DataCorruptedException.class, () -> codec.decode("{\"format\":\"something-else\"}".getBytes()));
        assertThrows(DataCorruptedException.class, () -> codec.decode(new byte[0]));
    }

    @Test
    void secondInstanceCannotLockTheSameFolder() throws Exception {
        // AC-17
        Path lockFile = dir.resolve("panyaya.lock");
        try (InstanceLock first = InstanceLock.tryAcquire(lockFile)) {
            assertNotNull(first);
            assertNull(InstanceLock.tryAcquire(lockFile));
        }
        try (InstanceLock again = InstanceLock.tryAcquire(lockFile)) {
            assertNotNull(again, "lock is released after close");
        }
    }

    @Test
    void concurrentOperationsAreSerialised() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        long categoryId = bakery.categoryId("Bread");
        int threads = 4;
        int perThread = 10;
        Thread[] workers = new Thread[threads];
        final Exception[] failure = new Exception[1];
        for (int t = 0; t < threads; t++) {
            final int n = t;
            workers[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        bakery.ctx.products().createProduct("P" + n + "-" + i, "P" + n + "-" + i, categoryId,
                                ProductType.MANUFACTURED, Money.of(1000), 24, "");
                    }
                } catch (Exception e) {
                    failure[0] = e;
                }
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertNull(failure[0]);
        assertEquals(threads * perThread, bakery.ctx.products().getActiveProducts().size());
        TestBakery reopened = bakery.reopen();
        assertEquals(threads * perThread, reopened.ctx.products().getActiveProducts().size(), "no lost updates");
    }

    /** Wraps a real storage and can be told to fail the next write. */
    static class FailingStorage implements DataStorage {
        private final DataStorage delegate;
        boolean failNext;

        FailingStorage(DataStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean exists() {
            return delegate.exists();
        }

        @Override
        public byte[] read() throws IOException {
            return delegate.read();
        }

        @Override
        public void write(byte[] content) throws IOException {
            if (failNext) {
                failNext = false;
                throw new IOException("Simulated disk full");
            }
            delegate.write(content);
        }

        @Override
        public String describe() {
            return "failing storage";
        }
    }
}
