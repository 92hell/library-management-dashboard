package com.helmifr.library.report;

import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.fine.FinePolicy;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.report.ReportDtos.Summary;
import com.helmifr.library.report.ReportDtos.TitleCount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    static final int TOP = 10;
    static final int CSV_TOP = 100;

    private final ReportQueries queries;
    private final FinePolicy finePolicy;
    private final Clock clock;

    /** Most rows one CSV download may have (library.reports.max-csv-rows). */
    @org.springframework.beans.factory.annotation.Value("${library.reports.max-csv-rows:20000}")
    private int maxCsvRows;

    public ReportRange range(LocalDate from, LocalDate to) {
        return ReportRange.of(from, to, today());
    }

    public Summary summary(ReportRange r) {
        LocalDate today = today();
        long due = queries.dueInPeriod(r, today);
        long late = queries.lateInPeriod(r, today);
        Double lateRate = due == 0 ? null : Math.round(1000.0 * late / due) / 1000.0;
        Instant start = startOf(r.from());
        Instant end = startOf(r.to().plusDays(1));
        return new Summary(r.from(), r.to(), queries.loans(r), queries.returns(r), due, late, lateRate,
                queries.activeMembers(r), queries.newMembers(start, end), queries.finesCharged(start, end),
                queries.finesCollected(start, end), finePolicy.currency(),
                queries.mostBorrowed(r, TOP), queries.mostActiveMembers(r, TOP),
                Trends.weekly(r, queries.loansPerDay(r)),
                Trends.monthly(r, queries.returnsPerDay(r, false), queries.returnsPerDay(r, true)));
    }

    public byte[] loansCsv(ReportRange r) {
        LocalDate today = today();
        Csv csv = new Csv().row("Loan ID", "Borrowed", "Due", "Returned", "Status", "Days late", "Title", "Author",
                "Copy barcode", "Member", "Library card ID");
        List<Loan> rows = queries.loansIn(r, maxCsvRows + 1);
        if (rows.size() > maxCsvRows) {
            String limit = "%,d".formatted(maxCsvRows);
            throw new FieldValidationException("from", "More than {0} loans in this range; please choose a shorter range",
                    limit);
        }
        for (Loan l : rows) {
            csv.row(l.getId(), l.getBorrowDate(), l.getDueDate(), l.getReturnDate(), l.getStatus(), daysLate(l, today),
                    l.getBook().getTitle(), l.getBook().getAuthor().getName(),
                    l.getCopy() == null ? null : l.getCopy().getBarcode(),
                    l.getMember().getName(), l.getMember().getLibraryCardId());
        }
        return csv.bytes();
    }

    public byte[] mostBorrowedCsv(ReportRange r) {
        Csv csv = new Csv().row("Rank", "Title", "Author", "Loans");
        List<TitleCount> titles = queries.mostBorrowed(r, CSV_TOP);
        for (int i = 0; i < titles.size(); i++) {
            TitleCount t = titles.get(i);
            csv.row(i + 1, t.title(), t.authorName(), t.loans());
        }
        return csv.bytes();
    }

    private static long daysLate(Loan l, LocalDate today) {
        if (l.getDueDate() == null) {
            return 0;
        }
        LocalDate end = l.getStatus() == LoanStatus.RETURNED ? l.getReturnDate() : today;
        return end == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(l.getDueDate(), end));
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
