package app.panyaya;

import app.panyaya.exception.DataCorruptedException;
import app.panyaya.io.CsvReader;
import app.panyaya.io.CsvWriter;
import app.panyaya.io.ImportPreview;
import app.panyaya.model.Unit;
import app.panyaya.service.BackupInfo;
import app.panyaya.service.CsvService;
import app.panyaya.service.ProgressListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupAndCsvTest {

    @TempDir
    Path dir;

    @Test
    void backupThenChangeThenRestoreGivesBackTheBackupState() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        bakery.manufactured("Roti Tawar", "RT-TAWAR", 18000, 48);
        Path backup = dir.resolve("manual/test.panyabak");
        bakery.ctx.backups().createBackup(backup, "MANUAL", ProgressListener.NONE);

        BackupInfo info = bakery.ctx.backups().inspect(backup);
        assertEquals(1, info.getManifest().getCounts().get("products"));
        assertEquals(64, info.getManifest().getDataSha256().length());

        bakery.manufactured("Croissant", "PAS-CROIS", 15000, 24);
        assertEquals(2, bakery.ctx.products().getActiveProducts().size());

        bakery.ctx.backups().restore(backup, ProgressListener.NONE);
        assertEquals(1, bakery.ctx.products().getActiveProducts().size(), "state from the backup");
        List<Path> auto = bakery.ctx.backups().listBackups(bakery.ctx.backups().getAutoBackupDir());
        assertTrue(auto.stream().anyMatch(p -> p.getFileName().toString().startsWith("pre-restore-")),
                "the data before the restore was backed up first");

        TestBakery reopened = bakery.reopen();
        assertEquals(1, reopened.ctx.products().getActiveProducts().size(), "restore was written to disk");
    }

    @Test
    void damagedOrForeignBackupsAreRefusedBeforeAnythingChanges() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        bakery.manufactured("Roti Tawar", "RT-TAWAR", 18000, 48);
        Path good = dir.resolve("good.panyabak");
        bakery.ctx.backups().createBackup(good, "MANUAL", ProgressListener.NONE);
        byte[] dataFileBefore = Files.readAllBytes(bakery.ctx.getPaths().getDataFile());

        // tamper with the data entry but keep the old manifest -> checksum mismatch
        Path tampered = dir.resolve("tampered.panyabak");
        BackupInfo info = bakery.ctx.backups().inspect(good);
        try (OutputStream out = Files.newOutputStream(tampered); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("pan-ya-ya.json"));
            zip.write(Files.readString(bakery.ctx.getPaths().getDataFile()).replace("Roti Tawar", "Roti Palsu")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(new app.panyaya.persistence.JsonCodec().encodeObject(info.getManifest()));
            zip.closeEntry();
        }
        assertThrows(DataCorruptedException.class, () -> bakery.ctx.backups().restore(tampered, ProgressListener.NONE));

        Path notZip = dir.resolve("random.panyabak");
        Files.writeString(notZip, "not a zip file");
        assertThrows(DataCorruptedException.class, () -> bakery.ctx.backups().restore(notZip, ProgressListener.NONE));

        assertTrue(Arrays.equals(dataFileBefore, Files.readAllBytes(bakery.ctx.getPaths().getDataFile())));
    }

    @Test
    void dailyBackupIsCreatedOncePerDay() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        assertTrue(bakery.ctx.backups().createDailyBackupIfNeeded() != null);
        assertEquals(null, bakery.ctx.backups().createDailyBackupIfNeeded());
        assertEquals(1, bakery.ctx.backups().listBackups(bakery.ctx.backups().getAutoBackupDir()).size());
    }

    @Test
    void csvReaderHandlesQuotesBomSemicolonsAndLineBreaks() throws Exception {
        String text = "﻿name;note\n\"Roti; Tawar\";\"He said \"\"hi\"\"\"\n\"Multi\nline\";x\n";
        List<CsvReader.Row> rows = CsvReader.parse(text);
        assertEquals(2, rows.size());
        assertEquals("Roti; Tawar", rows.get(0).get("name"));
        assertEquals("He said \"hi\"", rows.get(0).get("note"));
        assertEquals("Multi\nline", rows.get(1).get("name"));
        assertEquals(2, rows.get(0).getLineNumber());
        assertEquals(3, rows.get(1).getLineNumber());

        assertEquals("\"a,b\"", CsvWriter.escape("a,b"));
        assertEquals("\"say \"\"x\"\"\"", CsvWriter.escape("say \"x\""));
        assertEquals("plain", CsvWriter.escape("plain"));
    }

    @Test
    void csvImportDryRunReportsEveryBadRowAndWritesNothing() throws Exception {
        // AC-12
        TestBakery bakery = new TestBakery(dir);
        Path csv = dir.resolve("products.csv");
        Files.writeString(csv, "name,sku,category,type,price,shelf_life_hours\n"
                + "White Bread,BRE-WHITE,Bread,MANUFACTURED,18000,48\n"
                + "Bad Price,BRE-BAD,Bread,MANUFACTURED,abc,48\n"
                + "Croissant,PAS-CROIS,Pastry,MANUFACTURED,15.000,24\n"
                + "Duplicate,BRE-WHITE,Bread,MANUFACTURED,1000,24\n");
        long saves = bakery.saveCount();

        ImportPreview<CsvService.ProductRow> preview = bakery.ctx.csv().previewProducts(csv);
        assertEquals(2, preview.getErrors().size());
        assertEquals(3, preview.getErrors().get(0).getLine());
        assertEquals("price", preview.getErrors().get(0).getColumn());
        assertEquals(5, preview.getErrors().get(1).getLine());
        assertFalse(preview.canCommit());
        assertEquals(saves, bakery.saveCount(), "dry run writes nothing");
        assertThrows(app.panyaya.exception.ValidationException.class, () -> bakery.ctx.csv().importProducts(preview));

        Files.writeString(csv, "name,sku,category,type,price,shelf_life_hours\n"
                + "White Bread,BRE-WHITE,Bread,MANUFACTURED,18000,48\n"
                + "Croissant,PAS-CROIS,Viennoiserie,MANUFACTURED,15.000,24\n");
        ImportPreview<CsvService.ProductRow> good = bakery.ctx.csv().previewProducts(csv);
        assertTrue(good.canCommit());
        assertEquals(2, bakery.ctx.csv().importProducts(good));
        assertEquals(2, bakery.ctx.products().getActiveProducts().size());
        assertTrue(bakery.ctx.products().getCategories(false).stream().anyMatch(c -> c.getName().equals("Viennoiserie")));
    }

    @Test
    void exportsAreReadableCsv() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        bakery.ingredient("BUTTER", "Butter, unsalted", Unit.G, "500", true);
        Path out = dir.resolve("exports/ingredients.csv");
        bakery.ctx.csv().exportIngredients(out);
        List<CsvReader.Row> rows = CsvReader.read(out);
        assertEquals(1, rows.size());
        assertEquals("Butter, unsalted", rows.get(0).get("name"), "comma survives quoting");
        assertEquals((byte) 0xEF, Files.readAllBytes(out)[0], "UTF-8 BOM for Excel");
    }
}
