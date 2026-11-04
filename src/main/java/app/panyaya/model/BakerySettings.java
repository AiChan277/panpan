package app.panyaya.model;

import app.panyaya.exception.ValidationException;
import app.panyaya.util.TextUtil;

import java.time.DateTimeException;
import java.time.ZoneId;

/** Bakery profile and configurable thresholds, edited on the Settings screen. */
public class BakerySettings {

    public static final String DEFAULT_TIME_ZONE = "Asia/Jakarta";

    private String bakeryName = "Pan-ya Ya Bakery";
    private String address = "";
    private String phone = "";
    private String receiptFooter = "Terima kasih! Thank you for your visit.";
    private String timeZone = DEFAULT_TIME_ZONE;
    /** Ingredient lots expiring within this many days are flagged on the dashboard. */
    private int nearExpiryDays = 3;
    /** Finished-goods batches expiring within this many hours are flagged. */
    private int nearExpiryHours = 6;
    /** How many automatic daily backups are kept. */
    private int autoBackupKeep = 14;
    private boolean starterRecipesImported;

    public void updateProfile(String name, String newAddress, String newPhone, String footer)
            throws ValidationException {
        String cleanName = TextUtil.normalize(name);
        if (cleanName.isEmpty() || cleanName.length() > 60) {
            throw new ValidationException("Bakery name must be 1-60 characters.");
        }
        this.bakeryName = cleanName;
        this.address = TextUtil.normalize(newAddress);
        this.phone = TextUtil.normalize(newPhone);
        this.receiptFooter = footer == null ? "" : footer.trim();
    }

    public void updateThresholds(int expiryDays, int expiryHours, int backupsToKeep, String zone)
            throws ValidationException {
        if (expiryDays < 0 || expiryDays > 60) {
            throw new ValidationException("Near-expiry days must be between 0 and 60.");
        }
        if (expiryHours < 0 || expiryHours > 72) {
            throw new ValidationException("Near-expiry hours must be between 0 and 72.");
        }
        if (backupsToKeep < 1 || backupsToKeep > 365) {
            throw new ValidationException("Backups to keep must be between 1 and 365.");
        }
        try {
            ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new ValidationException("Unknown time zone: " + zone);
        }
        this.nearExpiryDays = expiryDays;
        this.nearExpiryHours = expiryHours;
        this.autoBackupKeep = backupsToKeep;
        this.timeZone = zone;
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    public String getBakeryName() {
        return bakeryName;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getReceiptFooter() {
        return receiptFooter;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public int getNearExpiryDays() {
        return nearExpiryDays;
    }

    public int getNearExpiryHours() {
        return nearExpiryHours;
    }

    public int getAutoBackupKeep() {
        return autoBackupKeep;
    }

    public boolean isStarterRecipesImported() {
        return starterRecipesImported;
    }

    public void markStarterRecipesImported() {
        this.starterRecipesImported = true;
    }
}
