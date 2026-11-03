package com.helmifr.library.dashboard;

import com.helmifr.library.book.BookRepository;
import com.helmifr.library.fine.FinePolicy;
import com.helmifr.library.fine.FineService;
import com.helmifr.library.loan.LoanRepository;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.member.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** At-a-glance numbers for the librarian home page. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    /**
     * @param readyForPickup copies set aside on the holds shelf, waiting to be collected
     * @param unpaidFines    total of late fees not yet paid or waived, in {@code currency}
     */
    public record Summary(long pendingRequests, long readyForPickup, long activeLoans, long overdueLoans,
                          long dueToday, long books, long members, long unpaidFines, String currency) {}

    private final LoanRepository loans;
    private final BookRepository books;
    private final MemberRepository members;
    private final FineService fines;
    private final FinePolicy finePolicy;
    private final Clock clock;

    @GetMapping
    @Transactional(readOnly = true)
    public Summary summary() {
        LocalDate today = LocalDate.now(clock);
        return new Summary(
                loans.countByStatusAndDeletedFalse(LoanStatus.PENDING),
                loans.countByStatusAndDeletedFalse(LoanStatus.READY),
                loans.countByStatusAndDeletedFalse(LoanStatus.ACTIVE),
                loans.countOverdue(today),
                loans.countDueOn(today),
                books.countByDeletedFalse(),
                members.countByDeletedFalse(),
                fines.unpaidTotal(),
                finePolicy.currency());
    }
}
