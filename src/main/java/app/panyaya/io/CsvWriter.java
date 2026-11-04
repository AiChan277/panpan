package app.panyaya.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes CSV files that open correctly in Excel and LibreOffice.
 *
 * <p>Course topic <b>Java IO</b>: a {@link BufferedWriter} wrapped around an
 * {@link OutputStreamWriter} (UTF-8) around a file {@link OutputStream}. The file starts with a
 * UTF-8 byte-order mark so Excel shows characters such as "パン屋ヤ" correctly. Values containing
 * commas, quotes or line breaks are quoted (RFC 4180).</p>
 */
public final class CsvWriter {

    private static final char BOM = '﻿';

    private CsvWriter() {
    }

    public static void write(Path file, List<String> header, List<List<String>> rows) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(file);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.write(BOM);
            writeRow(writer, header);
            for (List<String> row : rows) {
                writeRow(writer, row);
            }
        }
    }

    private static void writeRow(BufferedWriter writer, List<String> values) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(values.get(i)));
        }
        writer.write(line.toString());
        writer.write("\r\n");
    }

    /** Quotes a value when needed: {@code He said "hi"} becomes {@code "He said ""hi"""}. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r") || value.contains(";") || value.startsWith(" ") || value.endsWith(" ");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
