package com.helmifr.library.loan;

import com.helmifr.library.book.Book;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.NotFoundException;
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

    public List<LoanResponse> list(Long memberId) {
        return loanService.findByMember(memberId);
    }

    @Transactional
    public LoanResponse request(Long memberId, Long bookId) {
        Member member = memberService.require(memberId);
        if (!member.isActive()) {
            throw new ConflictException("Your library card is inactive. Please contact the library.");
        }
        Book book = loanService.lockBook(bookId);
        if (loans.existsOpenForMemberAndBook(memberId, bookId)) {
            throw new ConflictException("You already have an open request or loan for this book.");
        }
        if (loans.countOpenByMember(memberId) >= policy.maxOpenPerMember()) {
            throw new ConflictException("You can have at most %d open requests or loans.".formatted(policy.maxOpenPerMember()));
        }

        Loan loan = new Loan();
        loan.setBook(book);
        loan.setMember(member);
        loan.setStatus(LoanStatus.PENDING);
        loan.setRequestedAt(clock.instant());
        loan = loans.save(loan);

        Audit.event("BORROW_REQUESTED", "loanId={} bookId={} memberId={}", loan.getId(), bookId, memberId);
        notifications.notifyLibrarians(NotificationType.BORROW_REQUESTED,
                "%s requested \"%s\".".formatted(member.getName(), book.getTitle()), LoanService.PENDING_LINK);
        return LoanResponse.from(loan, LocalDate.now(clock));
    }

    @Transactional
    public LoanResponse cancel(Long memberId, Long loanId) {
        Loan loan = loans.findByIdAndMemberIdAndDeletedFalse(loanId, memberId)
                .orElseThrow(() -> new NotFoundException("Loan", loanId));
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new ConflictException("Only pending requests can be cancelled.");
        }
        loan.setStatus(LoanStatus.CANCELLED);
        Audit.event("REQUEST_CANCELLED", "loanId={} memberId={}", loanId, memberId);
        return LoanResponse.from(loan, LocalDate.now(clock));
    }
}
