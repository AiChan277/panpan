package app.panyaya;

import app.panyaya.model.Money;
import app.panyaya.model.product.ProductType;
import app.panyaya.persistence.AppPaths;
import app.panyaya.persistence.StartupInspector;
import app.panyaya.service.PasswordHasher;
import app.panyaya.util.Formatters;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Smoke test of a packaged build: creates a bakery in a temporary folder, saves a product, reopens
 * the folder and checks the product is still there. Writes the result to
 * {@code %TEMP%\panyaya-self-test.txt} because a GUI launcher may not show console output.
 */
final class SelfTest {

    private SelfTest() {
    }

    static int run() {
        Path report = Path.of(System.getProperty("java.io.tmpdir"), "panyaya-self-test.txt");
        Path dir = null;
        try {
            dir = Files.createTempDirectory("panyaya-selftest");
            AppContext first = new AppContext(new AppPaths(dir), Clock.systemUTC(), new PasswordHasher(10_000));
            if (first.getStartupInspector().inspect().getState() != StartupInspector.State.FIRST_RUN) {
                throw new IllegalStateException("empty folder not detected as first run");
            }
            first.auth().setupNewBakery("Self Test", "owner", "Owner", "self-test-1".toCharArray());
            long category = first.products().getCategories(false).get(0).getId();
            first.products().createProduct("Japanese Milk Bread", "BRE-JMB", category, ProductType.MANUFACTURED,
                    Money.of(22000), 48, "");
            first.shutdown();

            AppContext second = new AppContext(new AppPaths(dir), Clock.systemUTC(), new PasswordHasher(10_000));
            if (second.getStartupInspector().inspect().getState() != StartupInspector.State.READY) {
                throw new IllegalStateException("saved data could not be reopened");
            }
            second.auth().login("owner", "self-test-1".toCharArray());
            if (second.products().getActiveProducts().size() != 1) {
                throw new IllegalStateException("product not persisted");
            }
            String money = Formatters.money(Money.of(18000));
            if (!"Rp 18.000".equals(money)) {
                throw new IllegalStateException("money format wrong: " + money);
            }
            second.shutdown();
            write(report, "OK Pan-ya Ya " + AppContext.APP_VERSION + " self-test passed (Java "
                    + System.getProperty("java.version") + ")");
            return 0;
        } catch (Exception e) {
            write(report, "FAILED: " + e);
            return 1;
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void write(Path report, String message) {
        PrintStream out = System.out;
        out.println(message);
        try {
            Files.writeString(report, message + System.lineSeparator());
        } catch (IOException ignored) {
            // the exit code still tells the result
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // temporary folder, cleaned by Windows eventually
        }
    }
}
