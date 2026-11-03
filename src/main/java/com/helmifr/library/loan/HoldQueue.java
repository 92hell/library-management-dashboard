package com.helmifr.library.loan;

import com.helmifr.library.book.Book;
import com.helmifr.library.book.Copy;
import com.helmifr.library.book.CopyAdded;
import com.helmifr.library.book.CopyRepository;
import com.helmifr.library.common.Audit;
import com.helmifr.library.notification.NotificationService;
import com.helmifr.library.notification.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the queue for a title: while a copy is on the shelf and someone is waiting, the copy is set
 * aside (READY) for the first in line, who is notified. Called whenever a copy may have become free:
 * a return, a cancelled/declined/expired hold, or a new copy.
 */
@Component
@RequiredArgsConstructor
public class HoldQueue {

    private final LoanRepository loans;
    private final CopyRepository copies;
    private final LoanPolicy policy;
    private final NotificationService notifications;
    private final Clock clock;
    private final LoanEvents history;
    private final com.helmifr.library.fine.FineService fines;
    private final com.helmifr.library.book.BookRepository books;

    /** The caller must hold the book's lock (LoanService.lockBook) or be in a transaction that does. */
    @Transactional
    public void serve(Book book) {
        List<Loan> queue = loans.findQueue(book.getId());
        for (Loan next : queue) {
            // Members who could not collect (inactive card, unpaid fines) keep their place but are
            // skipped; they are served again when that changes (see onFinesSettled).
            if (!next.getMember().isActive() || !fines.canBorrow(next.getMember())) {
                continue;
            }
            Optional<Copy> free = freeCopy(book);
            if (free.isEmpty()) {
                return;
            }
            setAside(next, free.get(), NotificationType.HOLD_READY,
                    "A copy of \"{0}\" is ready for pickup until {1}.");
            history.record(next, LoanEventType.READY, "Copy %s set aside until %s".formatted(
                    free.get().getBarcode(), next.getReadyUntil()));
        }
    }

    /** Sets {@code copy} aside for {@code loan} and tells the member ({0} = title, {1} = collect-by date). */
    void setAside(Loan loan, Copy copy, NotificationType type, String messagePattern) {
        LocalDate readyUntil = LocalDate.now(clock).plusDays(policy.pickupDays());
        loan.setStatus(LoanStatus.READY);
        loan.setCopy(copy);
        loan.setReadyUntil(readyUntil);
        Audit.event("HOLD_READY", "loanId={} bookId={} memberId={} copy={} until={}", loan.getId(),
                loan.getBook().getId(), loan.getMember().getId(), copy.getBarcode(), readyUntil);
        notifications.notifyMember(loan.getMember().getId(), type, LoanService.MY_LOANS_LINK, messagePattern,
                loan.getBook().getTitle(), readyUntil);
    }

    Optional<Copy> freeCopy(Book book) {
        return copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).stream()
                .filter(c -> !loans.isCopyOnLoan(c.getId(), null))
                .findFirst();
    }

    /** A librarian added a copy: someone may be waiting for it. */
    /** A member paid their fines: serve the titles they are queued for (they may be next in line). */
    @EventListener
    @Transactional
    public void onFinesSettled(com.helmifr.library.fine.FinesSettled event) {
        loans.findWaitlistedBooksOfMember(event.memberId()).forEach(bookId ->
                books.findActiveForUpdate(bookId).ifPresent(this::serve));
    }

    @EventListener
    public void onCopyAdded(CopyAdded event) {
        serve(event.book());
    }
}
