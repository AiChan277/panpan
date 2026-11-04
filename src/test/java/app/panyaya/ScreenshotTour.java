package app.panyaya;

import app.panyaya.persistence.AppPaths;
import app.panyaya.service.PasswordHasher;
import app.panyaya.ui.MainController;
import app.panyaya.ui.common.TaskRunner;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Developer tool (test sources only): opens every screen of the real UI on a demo data folder and
 * saves a PNG snapshot of each. Proves that all FXML files load and all controllers initialise.
 *
 * <pre>java ... app.panyaya.ScreenshotTour local-data target/screenshots</pre>
 */
public final class ScreenshotTour {

    private static final String[] VIEWS = {"Dashboard", "Products", "Recipes", "Ingredients", "Inventory", "Suppliers",
            "Production", "Pos", "SalesHistory", "Waste", "Reports", "Users", "Settings", "Backup"};

    private ScreenshotTour() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        java.util.Locale.setDefault(java.util.Locale.UK);
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();

        AppContext ctx = new AppContext(new AppPaths(dataDir), Clock.systemUTC(), new PasswordHasher());
        ctx.getStartupInspector().inspect();
        ctx.auth().login("owner", "owner-demo-1".toCharArray());

        final Stage[] stage = new Stage[1];
        final MainController[] main = new MainController[1];
        final Scene[] scene = new Scene[1];
        runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(ScreenshotTour.class.getResource("/app/panyaya/ui/MainView.fxml"));
            Parent root = loader.load();
            scene[0] = new Scene(root, 1366, 820);
            scene[0].getStylesheets().add(ScreenshotTour.class.getResource(PanyaApp.CSS).toExternalForm());
            stage[0] = new Stage();
            stage[0].setScene(scene[0]);
            stage[0].show();
            main[0] = loader.getController();
            main[0].start(ctx, new TaskRunner(ctx.getBackgroundExecutor()), stage[0], () -> { });
        });
        for (String view : VIEWS) {
            runOnFx(() -> main[0].showView(view));
            Thread.sleep(1500); // let background tasks (dashboard, reports) finish
            runOnFx(() -> {
                WritableImage image = scene[0].snapshot(null);
                writePng(image, out.resolve(view + ".png"));
            });
            System.out.println("captured " + view);
        }
        // login and setup screens
        for (String fxml : new String[]{"LoginView", "SetupView"}) {
            runOnFx(() -> {
                Parent root = new FXMLLoader(ScreenshotTour.class.getResource("/app/panyaya/ui/" + fxml + ".fxml")).load();
                scene[0].setRoot(root);
            });
            Thread.sleep(300);
            runOnFx(() -> writePng(scene[0].snapshot(null), out.resolve(fxml + ".png")));
            System.out.println("captured " + fxml);
        }
        ctx.shutdown();
        Platform.exit();
        System.exit(0);
    }

    private interface FxWork {
        void run() throws Exception;
    }

    private static void runOnFx(FxWork work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Exception[] error = new Exception[1];
        Platform.runLater(() -> {
            try {
                work.run();
            } catch (Exception e) {
                error[0] = e;
            } finally {
                done.countDown();
            }
        });
        done.await();
        if (error[0] != null) {
            throw error[0];
        }
    }

    /** Minimal PNG encoder (RGBA), so no extra module (javafx.swing / java.desktop) is needed. */
    private static void writePng(WritableImage image, Path file) throws IOException {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(raw)) {
            for (int y = 0; y < h; y++) {
                deflate.write(0);
                for (int x = 0; x < w; x++) {
                    int argb = reader.getArgb(x, y);
                    deflate.write((argb >> 16) & 0xFF);
                    deflate.write((argb >> 8) & 0xFF);
                    deflate.write(argb & 0xFF);
                    deflate.write((argb >> 24) & 0xFF);
                }
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(png);
        data.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(ihdr);
        header.writeInt(w);
        header.writeInt(h);
        header.write(new byte[]{8, 6, 0, 0, 0});
        chunk(data, "IHDR", ihdr.toByteArray());
        chunk(data, "IDAT", raw.toByteArray());
        chunk(data, "IEND", new byte[0]);
        Files.write(file, png.toByteArray());
    }

    private static void chunk(DataOutputStream out, String type, byte[] content) throws IOException {
        byte[] typeBytes = type.getBytes("US-ASCII");
        out.writeInt(content.length);
        out.write(typeBytes);
        out.write(content);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(content);
        out.writeInt((int) crc.getValue());
    }
}
