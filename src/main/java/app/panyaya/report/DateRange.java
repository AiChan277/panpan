package app.panyaya.report;

import app.panyaya.util.Formatters;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** An inclusive range of business dates, with the usual presets. */
public class DateRange {

    private final LocalDate from;
    private final LocalDate to;

    public DateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both dates are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end date is before the start date");
        }
        this.from = from;
        this.to = to;
    }

    public static DateRange today(LocalDate today) {
        return new DateRange(today, today);
    }

    public static DateRange yesterday(LocalDate today) {
        return new DateRange(today.minusDays(1), today.minusDays(1));
    }

    public static DateRange thisWeek(LocalDate today) {
        return new DateRange(today.with(DayOfWeek.MONDAY), today);
    }

    public static DateRange thisMonth(LocalDate today) {
        return new DateRange(today.withDayOfMonth(1), today);
    }

    public static DateRange lastMonth(LocalDate today) {
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);
        return new DateRange(firstOfThisMonth.minusMonths(1), firstOfThisMonth.minusDays(1));
    }

    public static DateRange lastDays(LocalDate today, int days) {
        return new DateRange(today.minusDays(days - 1L), today);
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    /** Every date in the range, in order. */
    public List<LocalDate> days() {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            result.add(day);
        }
        return result;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    @Override
    public String toString() {
        return from.equals(to) ? Formatters.date(from) : Formatters.date(from) + " - " + Formatters.date(to);
    }
}
