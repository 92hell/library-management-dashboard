package com.helmifr.library.loan;

import com.helmifr.library.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Records a loan's history: called wherever a loan changes, next to the audit log entry. */
@Component
@RequiredArgsConstructor
public class LoanEvents {

    /** One history entry as shown to librarians and on receipts. */
    public record View(LoanEventType type, LoanStatus status, Instant occurredAt, String actor, String detail) {}

    private final LoanEventRepository events;
    private final Clock clock;

    public void record(Loan loan, LoanEventType type, String detail) {
        String user = CurrentUser.username();
        recordAt(loan, type, loan.getStatus(), detail, clock.instant(), "anonymous".equals(user) ? "system" : user);
    }

    /** An entry with an explicit time, status and actor: for importing or seeding past loans. */
    public void recordAt(Loan loan, LoanEventType type, LoanStatus status, String detail, Instant at, String actor) {
        LoanEvent e = new LoanEvent();
        e.setLoan(loan);
        e.setType(type);
        e.setStatus(status);
        e.setOccurredAt(at.truncatedTo(ChronoUnit.SECONDS));
        e.setActor(actor);
        e.setDetail(detail == null ? null : detail.length() > 255 ? detail.substring(0, 255) : detail);
        events.save(e);
    }

    public void record(Loan loan, LoanEventType type) {
        record(loan, type, null);
    }

    public List<View> of(Long loanId) {
        return events.findAllByLoanIdOrderByIdAsc(loanId).stream()
                .map(e -> new View(e.getType(), e.getStatus(), e.getOccurredAt(), e.getActor(), e.getDetail()))
                .toList();
    }
}
