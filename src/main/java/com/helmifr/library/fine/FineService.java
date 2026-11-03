package com.helmifr.library.fine;

import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.FieldValidationException;
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
import java.util.List;
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
    private final FinePaymentRepository payments;

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
        if (fine.getPaidAmount() > 0) {
            throw new ConflictException("The late fee for this loan is already partly paid; a correction cannot change it.");
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
            return amount == 0 ? null : new LoanFine(amount, policy.currency(), FineStatus.ACCRUING, amount);
        }
        if (loan.getStatus() == LoanStatus.RETURNED && daysLate(loan.getDueDate(), loan.getReturnDate()) > 0) {
            return fines.findByLoanId(loan.getId())
                    .map(f -> new LoanFine(f.getAmount(), policy.currency(), f.getStatus(), f.outstanding()))
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

    /**
     * Records a payment towards the fee: {@code amount} (1 up to what is still due), or everything still
     * due when null. The fine is paid once nothing is left.
     */
    @Transactional
    public FineResponse pay(Long id, Long amount) {
        Fine fine = requireUnpaid(id);
        long due = fine.outstanding();
        long paying = amount == null ? due : amount;
        if (paying < 1 || paying > due) {
            throw new FieldValidationException("amount", "must be between 1 and {0}",
                    policy.format(due));
        }
        boolean wasBlocked = policy.blocks(fines.sumUnpaidByMember(fine.getMember().getId()));
        FinePayment payment = new FinePayment();
        payment.setFine(fine);
        payment.setAmount(paying);
        payment.setPaidAt(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        payment.setRecordedBy(history.currentActorName());
        payments.save(payment);
        fine.setPaidAmount(fine.getPaidAmount() + paying);
        long left = fine.getAmount() - fine.getPaidAmount();
        Audit.event("FINE_PAID", "fineId={} memberId={} amount={} left={} by={}", id, fine.getMember().getId(),
                paying, left, CurrentUser.username());
        history.record(fine.getLoan(), LoanEventType.FINE_PAID, left == 0
                ? "Paid %s; late fee settled".formatted(policy.format(paying))
                : "Paid %s; %s still due".formatted(policy.format(paying), policy.format(left)));
        if (left == 0) {
            settle(fine, FineStatus.PAID, null, wasBlocked);
        } else {
            fines.flush();
            if (wasBlocked && canBorrow(fine.getMember())) {
                events.publishEvent(new FinesSettled(fine.getMember().getId()));
            }
        }
        return view(fine);
    }

    @Transactional
    public FineResponse waive(Long id, String reason) {
        Fine fine = requireUnpaid(id);
        long forgiven = fine.outstanding();
        settle(fine, FineStatus.WAIVED, reason.trim());
        Audit.event("FINE_WAIVED", "fineId={} memberId={} amount={} by={}", id, fine.getMember().getId(),
                forgiven, CurrentUser.username());
        history.record(fine.getLoan(), LoanEventType.FINE_WAIVED,
                "Waived %s: %s".formatted(policy.format(forgiven), reason.trim()));
        return view(fine);
    }

    private void settle(Fine fine, FineStatus status, String note) {
        settle(fine, status, note, policy.blocks(fines.sumUnpaidByMember(fine.getMember().getId())));
    }

    private void settle(Fine fine, FineStatus status, String note, boolean wasBlocked) {
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
        return FineResponse.from(fine, policy.currency(), paymentsOf(fine.getId()));
    }

    /** Every payment towards the fine, oldest first. */
    public List<FineDtos.PaymentView> paymentsOf(Long fineId) {
        return payments.findAllByFineIdOrderByIdAsc(fineId).stream()
                .map(p -> new FineDtos.PaymentView(p.getAmount(), p.getPaidAt(), p.getRecordedBy()))
                .toList();
    }

    private static long daysLate(LocalDate due, LocalDate returned) {
        return due == null || returned == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(due, returned));
    }
}
