package app.panyaya;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.Money;
import app.panyaya.model.product.Product;
import app.panyaya.model.product.ProductType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductServiceTest {

    @TempDir
    Path dir;

    @Test
    void createsProductFromUserInputAndPersistsIt() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        Product created = bakery.ctx.products().createProduct("  Japanese   Milk Bread ", "bre jmb",
                bakery.categoryId("Bread"), ProductType.MANUFACTURED, Money.of(22000), 48, "Hokkaido style");

        assertEquals("Japanese Milk Bread", created.getName(), "name is normalized");
        assertEquals("BRE-JMB", created.getSku(), "SKU is upper-cased, spaces become hyphens");

        // the product is in the JSON file on disk and survives a restart
        String json = Files.readString(bakery.ctx.getPaths().getDataFile());
        assertTrue(json.contains("\"Japanese Milk Bread\""));
        assertTrue(json.contains("\"22000.0000\""), "money is stored as a decimal string");

        TestBakery restarted = bakery.reopen();
        Product reloaded = restarted.ctx.products().getProduct(created.getId());
        assertNotNull(reloaded);
        assertEquals(0, Money.of(22000).compareTo(reloaded.getSellingPrice()));
    }

    @Test
    void rejectsDuplicateSkuCaseInsensitiveAndSavesNothing() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        bakery.manufactured("Roti Tawar", "RT-TAWAR", 18000, 48);
        long savesBefore = bakery.saveCount();
        byte[] fileBefore = Files.readAllBytes(bakery.ctx.getPaths().getDataFile());

        assertThrows(ValidationException.class, () -> bakery.manufactured("Another", "rt-tawar", 1000, 24));

        assertEquals(savesBefore, bakery.saveCount());
        assertEquals(1, bakery.ctx.products().getActiveProducts().size(), "memory rolled back");
        assertTrue(java.util.Arrays.equals(fileBefore, Files.readAllBytes(bakery.ctx.getPaths().getDataFile())),
                "data file unchanged");
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        assertThrows(ValidationException.class, () -> bakery.manufactured("Bad SKU", "a", 1000, 24));
        assertThrows(ValidationException.class, () -> bakery.manufactured("", "OK-1", 1000, 24));
        assertThrows(ValidationException.class, () -> bakery.manufactured("Free", "OK-2", 0, 24));
        assertThrows(ValidationException.class, () -> bakery.manufactured("Forever", "OK-3", 1000, 0));
    }

    @Test
    void priceChangeIsRecordedInHistoryAndArchivedProductsLeaveTheActiveList() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        Product product = bakery.manufactured("Croissant", "PAS-CROIS", 15000, 24);
        bakery.ctx.products().updateProduct(product.getId(), "Croissant", "PAS-CROIS", product.getCategoryId(),
                ProductType.MANUFACTURED, Money.of(16000), 24, "");

        Product updated = bakery.ctx.products().getProduct(product.getId());
        assertEquals(1, updated.getPriceHistory().size());
        assertEquals(0, Money.of(15000).compareTo(updated.getPriceHistory().get(0).getOldPrice()));

        bakery.ctx.products().setArchived(product.getId(), true);
        assertTrue(bakery.ctx.products().getActiveProducts().isEmpty());
        assertFalse(bakery.ctx.products().getProduct(product.getId()).isActive());
        assertEquals(1, bakery.ctx.products().search("crois", null, null, true).size(), "search is case-insensitive");
    }

    @Test
    void suggestsUniqueSku() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        assertEquals("BRE-JMB", bakery.ctx.products().suggestSku("Japanese Milk Bread", bakery.categoryId("Bread")));
        bakery.manufactured("Japanese Milk Bread", "BRE-JMB", 20000, 48);
        assertEquals("BRE-JMB-2", bakery.ctx.products().suggestSku("Japanese Milk Bread", bakery.categoryId("Bread")));
    }
}
