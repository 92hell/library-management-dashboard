package com.helmifr.library.report;

import com.helmifr.library.common.FieldValidationException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** An inclusive date range for a report: at most a year; by default the last 30 days. */
public record ReportRange(LocalDate from, LocalDate to) {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    static ReportRange of(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (end.isBefore(start)) {
            throw new FieldValidationException("to", "must not be before the start date");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new FieldValidationException("from", "A report can cover at most a year");
        }
        return new ReportRange(start, end);
    }

    String fileSuffix() {
        return from + "-to-" + to;
    }
}
