package app.panyaya.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a CSV dry run: the rows that would be imported and every problem found.
 * The import can only be committed when there are no errors (all-or-nothing).
 *
 * @param <T> type of the imported rows
 */
public class ImportPreview<T> {

    private final List<T> rows;
    private final List<ImportError> errors;
    private final int totalRows;

    public ImportPreview(List<T> rows, List<ImportError> errors, int totalRows) {
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.totalRows = totalRows;
    }

    public boolean canCommit() {
        return errors.isEmpty() && !rows.isEmpty();
    }

    public List<T> getRows() {
        return rows;
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    public int getTotalRows() {
        return totalRows;
    }
}
