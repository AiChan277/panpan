package app.panyaya.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Application log in {@code %LOCALAPPDATA%\PanyaYa\logs} (5 rotating files of 1 MB), using the
 * JDK's own java.util.logging - no extra library. Unexpected errors are written here so the user
 * never has to read a stack trace.
 */
public final class AppLog {

    private static final Logger LOGGER = Logger.getLogger("app.panyaya");

    private AppLog() {
    }

    public static void configure(Path logDir) {
        try {
            Files.createDirectories(logDir);
            FileHandler handler = new FileHandler(logDir.resolve("panyaya-%g.log").toString(), 1_000_000, 5, true);
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
            LOGGER.setLevel(Level.INFO);
        } catch (IOException e) {
            LOGGER.warning("File logging unavailable: " + e.getMessage());
        }
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void error(String message, Throwable error) {
        LOGGER.log(Level.SEVERE, message, error);
    }
}
