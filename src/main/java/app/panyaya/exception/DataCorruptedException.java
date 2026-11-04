package app.panyaya.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A data or backup file cannot be trusted: invalid JSON, wrong format, unsupported version or failed
 * integrity rules. The application never opens such a file as if it were empty.
 */
public class DataCorruptedException extends PersistenceException {

    private final List<String> problems;

    public DataCorruptedException(String message) {
        super(message);
        this.problems = Collections.singletonList(message);
    }

    public DataCorruptedException(String message, Throwable cause) {
        super(message, cause);
        this.problems = Collections.singletonList(message);
    }

    public DataCorruptedException(String message, List<String> problems) {
        super(message + " (" + problems.size() + " problem(s)): " + firstFew(problems));
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    public List<String> getProblems() {
        return problems;
    }

    private static String firstFew(List<String> problems) {
        int count = Math.min(3, problems.size());
        return String.join("; ", problems.subList(0, count)) + (problems.size() > count ? "; ..." : "");
    }
}
