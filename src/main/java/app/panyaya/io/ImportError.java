package app.panyaya.io;

/** One problem found in a CSV import file: line, column and a readable message. */
public class ImportError {

    private final int line;
    private final String column;
    private final String message;

    public ImportError(int line, String column, String message) {
        this.line = line;
        this.column = column;
        this.message = message;
    }

    public int getLine() {
        return line;
    }

    public String getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "Line " + line + (column == null || column.isEmpty() ? "" : " [" + column + "]") + ": " + message;
    }
}
