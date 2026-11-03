package com.helmifr.library.report;

import com.helmifr.library.report.ReportDtos.MonthReturns;
import com.helmifr.library.report.ReportDtos.WeekLoans;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Groups daily counts into the buckets the report charts show: weeks (Monday to Sunday) and
 * calendar months. Every bucket in the range is listed, also when it is empty; the first and
 * last are cut to the range, so their dates say which days they cover.
 */
final class Trends {
    private Trends() {}

    static List<WeekLoans> weekly(ReportRange r, Map<LocalDate, Long> loansPerDay) {
        List<WeekLoans> weeks = new ArrayList<>();
        for (LocalDate start = r.from(); !start.isAfter(r.to()); ) {
            LocalDate end = min(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), r.to());
            weeks.add(new WeekLoans(start, end, sum(loansPerDay, start, end)));
            start = end.plusDays(1);
        }
        return weeks;
    }

    static List<MonthReturns> monthly(ReportRange r, Map<LocalDate, Long> returnsPerDay, Map<LocalDate, Long> latePerDay) {
        List<MonthReturns> months = new ArrayList<>();
        for (LocalDate start = r.from(); !start.isAfter(r.to()); ) {
            LocalDate end = min(start.with(TemporalAdjusters.lastDayOfMonth()), r.to());
            long late = sum(latePerDay, start, end);
            months.add(new MonthReturns(start, end, sum(returnsPerDay, start, end) - late, late));
            start = end.plusDays(1);
        }
        return months;
    }

    private static long sum(Map<LocalDate, Long> perDay, LocalDate from, LocalDate to) {
        long total = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            total += perDay.getOrDefault(d, 0L);
        }
        return total;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
