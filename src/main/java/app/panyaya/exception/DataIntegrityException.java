package app.panyaya.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The data check before saving found an inconsistency (e.g. a lot whose quantity does not match its
 * movements). This indicates a program bug; the change is refused and nothing is saved.
 */
public class DataIntegrityException extends PersistenceException {

    private final List<String> problems;

    public DataIntegrityException(List<String> problems) {
        super("The change was refused because it would make the data inconsistent: "
                + String.join("; ", problems.subList(0, Math.min(3, problems.size()))));
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    public List<String> getProblems() {
        return problems;
    }
}
