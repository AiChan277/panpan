package app.panyaya.service;

import java.nio.file.Path;

/** A verified backup file: where it is, its manifest and the decoded data bytes. */
public class BackupInfo {

    private final Path file;
    private final BackupManifest manifest;
    private final byte[] dataBytes;

    public BackupInfo(Path file, BackupManifest manifest, byte[] dataBytes) {
        this.file = file;
        this.manifest = manifest;
        this.dataBytes = dataBytes;
    }

    public Path getFile() {
        return file;
    }

    public BackupManifest getManifest() {
        return manifest;
    }

    byte[] getDataBytes() {
        return dataBytes;
    }
}
