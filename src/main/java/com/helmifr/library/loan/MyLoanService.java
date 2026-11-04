package com.helmifr.library.loan;

import com.helmifr.library.book.Book;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.fine.FineService;
import com.helmifr.library.member.Member;
import com.helmifr.library.member.MemberService;
import com.helmifr.library.notification.NotificationService;
import com.helmifr.library.notification.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A customer's own loans and borrow requests. Every method is scoped to the given member. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyLoanService {

    private final LoanRepository loans;
    private final LoanService loanService;
    private final MemberService memberService;
    private final NotificationService notifications;
    private final LoanPolicy policy;
    private final Clock clock;
    private final HoldQueue holdQueue;
    private final LoanViews views;
    private final FineService fines;
    private final LoanEvents history;

    /** What needs the member's attention first, then their history (see {@link MyLoanOrder}). */
    public List<LoanResponse> list(Long memberId) {
        return loanService.findByMember(memberId).stream()
                .sorted(MyLoanOrder.attentionFirst(clock.getZone()))
                .toList();
    }

    @Transactional
    public LoanResponse request(Long memberId, Long bookId) {
        Member member = memberService.lock(memberId);
        if (!member.isActive()) {
            throw new ConflictException("Your library card is inactive. Please contact the library.");
        }
        if (member.cardExpiredOn(LocalDate.now(clock))) {
            throw new ConflictException("Your library card expired on {0}. Please renew it at the desk.",
                    member.getCardExpiresOn());
        }
        fines.ensureCanBorrow(member, true);
        Book book = loanService.lockBook(bookId);
        if (loans.existsOpenForMemberAndBook(memberId, bookId)) {
            throw new ConflictException("You already have an open request or loan for this book.");
        }
        if (loans.countOpenByMember(memberId) >= policy.maxOpenPerMember()) {
            throw new ConflictException("You can have at most {0} open requests or loans.", policy.maxOpenPerMember());
        }

        Loan loan = new Loan();
        loan.setBook(book);
        loan.setMember(member);
        loan.setStatus(LoanStatus.PENDING);
        loan.setRequestedAt(clock.instant());
        loan = loans.save(loan);

        Audit.event("BORROW_REQUESTED", "loanId={} bookId={} memberId={}", loan.getId(), bookId, memberId);
        history.record(loan, LoanEventType.REQUESTED);
        notifications.notifyLibrarians(NotificationType.BORROW_REQUESTED, LoanService.PENDING_LINK,
                "{0} requested \"{1}\".", member.getName(), book.getTitle());
        return views.of(loan);
    }

    /** Cancels the member's own request, in the queue or set aside (the copy then goes to the next in line). */
    @Transactional
    public LoanResponse cancel(Long memberId, Long loanId) {
        loans.findByIdAndMemberIdAndDeletedFalse(loanId, memberId).orElseThrow(() -> new NotFoundException("Loan", loanId));
        Loan loan = loanService.lockLoan(loanId);
        if (loan.getStatus() != LoanStatus.PENDING && loan.getStatus() != LoanStatus.WAITLISTED
                && loan.getStatus() != LoanStatus.READY) {
            throw new ConflictException("Only requests that are not yet collected can be cancelled.");
        }
        Book book = loan.getBook();
        boolean freesCopy = loan.getStatus() == LoanStatus.READY;
        loan.setStatus(LoanStatus.CANCELLED);
        loan.setReadyUntil(null);
        Audit.event("REQUEST_CANCELLED", "loanId={} memberId={}", loanId, memberId);
        history.record(loan, LoanEventType.CANCELLED);
        if (freesCopy) {
            holdQueue.serve(book);
        }
        return views.of(loan);
    }

    /** Renews the member's own loan (once, if nobody is waiting and it is not overdue). */
    @Transactional
    public LoanResponse renew(Long memberId, Long loanId) {
        loans.findByIdAndMemberIdAndDeletedFalse(loanId, memberId).orElseThrow(() -> new NotFoundException("Loan", loanId));
        return loanService.renew(loanService.lockLoan(loanId));
    }
}
