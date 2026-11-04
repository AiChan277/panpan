package app.panyaya.report;

import app.panyaya.model.BakeryData;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Base class of all reports.
 *
 * <p>Course topics <b>Abstraction</b> and <b>Polymorphism</b> (Template Method): {@link #generate}
 * is the same for every report - create the table, let the subclass fill it, return it. Each
 * subclass only says what its title and columns are and how to fill the rows. The Reports screen
 * holds a {@code List<ReportGenerator>} and calls {@code generate(...)} without knowing which
 * concrete report it is.</p>
 */
public abstract class ReportGenerator {

    /** The fixed algorithm; {@code final} so subclasses cannot change the steps. */
    public final Report generate(BakeryData data, DateRange range, Instant now, ZoneId zone) {
        Report report = new Report(getTitle(), range, getColumns(), now);
        fill(report, data, range, now, zone);
        return report;
    }

    public abstract String getTitle();

    public abstract String getDescription();

    protected abstract List<String> getColumns();

    protected abstract void fill(Report report, BakeryData data, DateRange range, Instant now, ZoneId zone);

    /** Stock reports show the current state and ignore the date range. */
    public boolean usesDateRange() {
        return true;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
