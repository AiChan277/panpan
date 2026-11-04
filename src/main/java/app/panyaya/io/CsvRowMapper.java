package app.panyaya.io;

import java.util.List;

/**
 * Converts one CSV row into an object of type {@code T}, or reports why it cannot.
 * Generic interface: one implementation each for products, ingredients and suppliers.
 *
 * @param <T> the type of object a row becomes
 */
public interface CsvRowMapper<T> {

    /** Column names that must exist in the header. */
    List<String> requiredColumns();

    /** @return the mapped object, or {@code null} if errors were added */
    T map(CsvReader.Row row, List<ImportError> errors);
}
