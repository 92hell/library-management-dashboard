package com.helmifr.library.fine;

import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.SearchPatterns;
import com.helmifr.library.fine.FineDtos.FineResponse;
import com.helmifr.library.fine.FineDtos.LoanFine;
import com.helmifr.library.fine.FineDtos.MyFines;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanEventType;
import com.helmifr.library.loan.LoanEvents;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.member.Member;
import com.helmifr.library.notification.NotificationService;
import com.helmifr.library.notification.NotificationType;
import com.helmifr.library.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Late fees. A fine is charged when a book comes back after its due date (see {@link FinePolicy});
 * a librarian records the payment or waives it with a reason. Unpaid fines above the policy's
 * threshold stop a member from borrowing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FineService {

    static final String MY_LOANS_LINK = "/my/loans";

    private final FineRepository fines;
    private final FinePolicy policy;
    private final NotificationService notifications;
    private final Clock clock;
    private final LoanEvents history;
    private final org.springframework.context.ApplicationEventPublisher events;

    /** Called when a loan is returned: charges the late fee, if any, and tells the member. */
    @Transactional
    public void chargeIfLate(Loan loan) {
        long daysLate = daysLate(loan.getDueDate(), loan.getReturnDate());
        long amount = policy.feeFor(daysLate);
        if (amount == 0 || fines.findByLoanId(loan.getId()).isPresent()) {
            return;
        }
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setMember(loan.getMember());
        fine.setAmount(amount);
        fine.setDaysLate((int) daysLate);
        fines.save(fine);
        Audit.event("FINE_CHARGED", "fineId={} loanId={} memberId={} amount={} daysLate={}", fine.getId(),
                loan.getId(), loan.getMember().getId(), amount, daysLate);
        history.record(loan, LoanEventType.FINE_CHARGED, "Late fee %s for %d day%s late".formatted(
                policy.format(amount), daysLate, daysLate == 1 ? "" : "s"));
        notifications.notifyMember(loan.getMember().getId(), NotificationType.FINE_ISSUED, MY_LOANS_LINK,
                daysLate == 1
                        ? "\"{0}\" came back {1} day late: a late fee of {2}. Please pay at the desk."
                        : "\"{0}\" came back {1} days late: a late fee of {2}. Please pay at the desk.",
                loan.getBook().getTitle(), daysLate, policy.format(amount));
    }

    /**
     * After a returned loan's dates were corrected: charges a fee that is now due, or adjusts an
     * unpaid one (waiving it when the loan is no longer late). A fee already paid or waived cannot
     * change; such a correction is refused.
     */
    @Transactional
    public void reassess(Loan loan) {
        long daysLate = daysLate(loan.getDueDate(), loan.getReturnDate());
        long amount = policy.feeFor(daysLate);
        var existing = fines.findByLoanId(loan.getId());
        if (existing.isEmpty()) {
            chargeIfLate(loan);
            return;
        }
        Fine fine = fines.findForUpdate(existing.get().getId()).orElseThrow();
        if (fine.getAmount() == amount && fine.getDaysLate() == daysLate) {
            return;
        }
        if (fine.getStatus() != FineStatus.UNPAID) {
            throw fine.getStatus() == FineStatus.PAID
                    ? new ConflictException("The late fee for this loan was already paid; a correction cannot change it.")
                    : new ConflictException("The late fee for this loan was already waived; a correction cannot change it.");
        }
        String before = policy.format(fine.getAmount());
        if (amount == 0) {
            settle(fine, FineStatus.WAIVED, "No longer late after a correction");
        } else {
            fine.setAmount(amount);
            fine.setDaysLate((int) daysLate);
        }
        Audit.event("FINE_ADJUSTED", "fineId={} loanId={} from={} to={} by={}", fine.getId(), loan.getId(), before,
                policy.format(amount), CurrentUser.username());
        history.record(loan, LoanEventType.FINE_ADJUSTED, amount == 0
                ? "Late fee %s cancelled: no longer late after a correction".formatted(before)
                : "Late fee changed from %s to %s after a correction".formatted(before, policy.format(amount)));
    }

    /** The fee shown on a loan: the fine charged for a late return, or the fee so far for a book still out. */
    public LoanFine onLoan(Loan loan, LocalDate today) {
        if (loan.getStatus() == LoanStatus.ACTIVE) {
            long amount = policy.feeFor(daysLate(loan.getDueDate(), today));
            return amount == 0 ? null : new LoanFine(amount, policy.currency(), FineStatus.ACCRUING);
        }
        if (loan.getStatus() == LoanStatus.RETURNED && daysLate(loan.getDueDate(), loan.getReturnDate()) > 0) {
            return fines.findByLoanId(loan.getId())
                    .map(f -> new LoanFine(f.getAmount(), policy.currency(), f.getStatus()))
                    .orElse(null);
        }
        return null;
    }

    /**
     * Refuses a new loan or request for a member whose unpaid fines reach the threshold.
     *
     * @param byMember the member is asking themselves (otherwise a librarian at the desk)
     */
    /** May the member borrow (unpaid fines below the limit)? */
    public boolean canBorrow(Member member) {
        return !policy.blocks(fines.sumUnpaidByMember(member.getId()));
    }

    public void ensureCanBorrow(Member member, boolean byMember) {
        long unpaid = fines.sumUnpaidByMember(member.getId());
        if (!policy.blocks(unpaid)) {
            return;
        }
        throw byMember
                ? new ConflictException("You have unpaid fines of {0}. Please pay them at the desk before borrowing more books.",
                        policy.format(unpaid))
                : new ConflictException("{0} has unpaid fines of {1}; record the payment before lending more books.",
                        member.getName(), policy.format(unpaid));
    }

    public MyFines mine(Long memberId) {
        long balance = fines.sumUnpaidByMember(memberId);
        return new MyFines(balance, policy.currency(), policy.blocks(balance), policy.blockAt(),
                fines.findAllByMemberIdAndLoanDeletedFalseOrderByIdDesc(memberId).stream().map(this::view).toList());
    }

    public PageResponse<FineResponse> search(String q, FineStatus status, Pageable pageable) {
        return PageResponse.of(fines.search(SearchPatterns.contains(q), status, pageable).map(this::view));
    }

    public long unpaidTotal() {
        return fines.sumUnpaid();
    }

    @Transactional
    public FineResponse pay(Long id) {
        Fine fine = requireUnpaid(id);
        settle(fine, FineStatus.PAID, null);
        Audit.event("FINE_PAID", "fineId={} memberId={} amount={} by={}", id, fine.getMember().getId(),
                fine.getAmount(), CurrentUser.username());
        history.record(fine.getLoan(), LoanEventType.FINE_PAID, "Paid " + policy.format(fine.getAmount()));
        return view(fine);
    }

    @Transactional
    public FineResponse waive(Long id, String reason) {
        Fine fine = requireUnpaid(id);
        settle(fine, FineStatus.WAIVED, reason.trim());
        Audit.event("FINE_WAIVED", "fineId={} memberId={} amount={} by={}", id, fine.getMember().getId(),
                fine.getAmount(), CurrentUser.username());
        history.record(fine.getLoan(), LoanEventType.FINE_WAIVED,
                "Waived %s: %s".formatted(policy.format(fine.getAmount()), reason.trim()));
        return view(fine);
    }

    private void settle(Fine fine, FineStatus status, String note) {
        boolean wasBlocked = policy.blocks(fines.sumUnpaidByMember(fine.getMember().getId()));
        fine.setStatus(status);
        fine.setSettledAt(clock.instant());
        fine.setSettledBy(CurrentUser.username());
        fine.setNote(note);
        fines.flush();
        if (wasBlocked && canBorrow(fine.getMember())) {
            events.publishEvent(new FinesSettled(fine.getMember().getId()));
        }
    }

    private Fine requireUnpaid(Long id) {
        Fine fine = fines.findForUpdate(id).orElseThrow(() -> new NotFoundException("Fine", id));
        if (fine.getStatus() != FineStatus.UNPAID) {
            throw fine.getStatus() == FineStatus.PAID
                    ? new ConflictException("This fine is already paid.")
                    : new ConflictException("This fine is already waived.");
        }
        return fine;
    }

    private FineResponse view(Fine fine) {
        return FineResponse.from(fine, policy.currency());
    }

    private static long daysLate(LocalDate due, LocalDate returned) {
        return due == null || returned == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(due, returned));
    }
}
