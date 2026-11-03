package com.helmifr.library.loan;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds LoanResponses, adding what needs a lookup: queue position and whether it can be renewed. */
@Component
@RequiredArgsConstructor
class LoanViews {

    private final LoanRepository loans;
    private final LoanPolicy policy;
    private final Clock clock;

    LoanResponse of(Loan loan) {
        LocalDate today = LocalDate.now(clock);
        return LoanResponse.from(loan, today, queuePosition(loan), canRenew(loan, today));
    }

    List<LoanResponse> ofAll(List<Loan> list) {
        return list.stream().map(this::of).toList();
    }

    /** Why a loan cannot be renewed, or null if it can. */
    String whyNotRenewable(Loan loan, LocalDate today) {
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            return "Only books on loan can be renewed.";
        }
        if (loan.isOverdue(today)) {
            return "Overdue loans cannot be renewed; please return the book.";
        }
        if (loan.getRenewalCount() >= policy.maxRenewals()) {
            return "This loan has already been renewed the maximum number of times.";
        }
        if (loans.anyoneWaitingFor(loan.getBook().getId())) {
            return "Someone is waiting for this book, so it cannot be renewed.";
        }
        return null;
    }

    private boolean canRenew(Loan loan, LocalDate today) {
        return loan.getStatus() == LoanStatus.ACTIVE && whyNotRenewable(loan, today) == null;
    }

    private Integer queuePosition(Loan loan) {
        if (loan.getStatus() != LoanStatus.WAITLISTED) {
            return null;
        }
        List<Loan> queue = loans.findQueue(loan.getBook().getId());
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(loan.getId())) {
                return i + 1;
            }
        }
        return null;
    }
}
