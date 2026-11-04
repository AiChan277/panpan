package app.panyaya.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small CSV parser written with plain Java IO (no library).
 *
 * <p>Supports: a header row, quoted fields containing commas, escaped quotes ({@code ""}) and line
 * breaks, an optional UTF-8 byte-order mark, and comma or semicolon as separator (Excel with
 * Indonesian regional settings saves with semicolons; the header line decides).</p>
 */
public final class CsvReader {

    private CsvReader() {
    }

    /** One data row: column name (lower-case) -> value, plus its line number in the file. */
    public static class Row {
        private final int lineNumber;
        private final Map<String, String> values;

        Row(int lineNumber, Map<String, String> values) {
            this.lineNumber = lineNumber;
            this.values = values;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        /** Trimmed value of a column; empty string when the column is missing. */
        public String get(String column) {
            String value = values.get(column.toLowerCase(Locale.ROOT));
            return value == null ? "" : value.trim();
        }

        public boolean has(String column) {
            return values.containsKey(column.toLowerCase(Locale.ROOT));
        }
    }

    /** A parsed record before it is matched with the header. */
    private static class Record {
        final int line;
        final List<String> fields = new ArrayList<>();

        Record(int line) {
            this.line = line;
        }
    }

    public static List<Row> read(Path file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream in = Files.newInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                text.append(buffer, 0, count);
            }
        }
        return parse(text.toString());
    }

    /** Parses CSV text; the first record is the header. */
    public static List<Row> parse(String text) {
        String content = text;
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1); // byte-order mark
        }
        char separator = chooseSeparator(content);
        List<Record> records = splitRecords(content, separator);

        List<Row> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<String> header = new ArrayList<>();
        for (String name : records.get(0).fields) {
            header.add(name.trim().toLowerCase(Locale.ROOT));
        }
        for (int i = 1; i < records.size(); i++) {
            Record record = records.get(i);
            if (isEmpty(record)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                values.put(header.get(c), c < record.fields.size() ? record.fields.get(c) : "");
            }
            rows.add(new Row(record.line, values));
        }
        return rows;
    }

    private static char chooseSeparator(String content) {
        int end = content.indexOf('\n');
        String headerLine = end < 0 ? content : content.substring(0, end);
        int commas = headerLine.length() - headerLine.replace(",", "").length();
        int semicolons = headerLine.length() - headerLine.replace(";", "").length();
        return semicolons > commas ? ';' : ',';
    }

    /** Character-by-character state machine: inside quotes, separators and line breaks are data. */
    private static List<Record> splitRecords(String content, char separator) {
        List<Record> records = new ArrayList<>();
        int line = 1;
        Record current = new Record(line);
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    boolean escapedQuote = i + 1 < content.length() && content.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                inQuotes = true;
            } else if (c == separator) {
                current.fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                current.fields.add(field.toString());
                field.setLength(0);
                records.add(current);
                line++;
                current = new Record(line);
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !current.fields.isEmpty()) {
            current.fields.add(field.toString());
            records.add(current);
        }
        return records;
    }

    private static boolean isEmpty(Record record) {
        for (String value : record.fields) {
            if (!value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
