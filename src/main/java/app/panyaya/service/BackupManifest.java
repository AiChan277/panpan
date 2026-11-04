package app.panyaya.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code manifest.json} inside every {@code .panyabak} backup ZIP. It records what the backup
 * contains and the SHA-256 checksum of the data file, so damage is detected before restoring.
 */
public class BackupManifest {

    public static final String FORMAT = "pan-ya-ya/backup";
    public static final int BACKUP_FORMAT_VERSION = 1;

    String format = FORMAT;
    int backupFormatVersion = BACKUP_FORMAT_VERSION;
    String appVersion;
    int schemaVersion;
    long saveCount;
    Instant createdAt;
    String reason;
    String bakeryName;
    String dataEntry;
    long dataBytes;
    String dataSha256;
    Map<String, Integer> counts = new LinkedHashMap<>();

    public String getFormat() {
        return format;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public long getSaveCount() {
        return saveCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReason() {
        return reason;
    }

    public String getBakeryName() {
        return bakeryName;
    }

    public long getDataBytes() {
        return dataBytes;
    }

    public String getDataSha256() {
        return dataSha256;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }
}
