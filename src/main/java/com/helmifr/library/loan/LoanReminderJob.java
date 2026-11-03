package com.helmifr.library.loan;

import com.helmifr.library.common.Audit;
import com.helmifr.library.notification.NotificationService;
import com.helmifr.library.notification.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily job that reminds customers about due and overdue books. Each loan gets at most one
 * "due soon" and one "overdue" notice (flags on the loan), so re-running is harmless.
 */
@Component
@RequiredArgsConstructor
public class LoanReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LoanReminderJob.class);

    private final LoanRepository loans;
    private final NotificationService notifications;
    private final LoanPolicy policy;
    private final Clock clock;
    private final HoldQueue holdQueue;
    private final LoanService loanService;
    private final LoanEvents history;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    @Scheduled(cron = "${library.loans.reminder-cron}")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        // Called on this bean directly, so @Transactional would not apply: use explicit transactions.
        expireUncollectedHolds(today);
        transactions.executeWithoutResult(status -> sendReminders(today));
    }

    /**
     * Holds not collected by their pickup date expire; the copy goes to the next in the queue.
     * Each hold is handled in its own transaction, under its book's lock, and re-checked there, so a
     * hold collected or cancelled meanwhile is left alone and one failure does not undo the others.
     */
    public void expireUncollectedHolds(LocalDate today) {
        List<Long> candidates = transactions.execute(status ->
                loans.findUncollectedHolds(today).stream().map(Loan::getId).toList());
        int expired = 0;
        for (Long id : candidates) {
            Boolean done = transactions.execute(status -> expire(id, today));
            if (Boolean.TRUE.equals(done)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Expired {} uncollected hold(s) for {}", expired, today);
        }
    }

    private boolean expire(Long loanId, LocalDate today) {
        Loan loan = loanService.lockLoan(loanId);
        if (loan.getStatus() != LoanStatus.READY || loan.getReadyUntil() == null || !loan.getReadyUntil().isBefore(today)) {
            return false;
        }
        loan.setStatus(LoanStatus.EXPIRED);
        loan.setReadyUntil(null);
        Audit.event("HOLD_EXPIRED", "loanId={} memberId={}", loan.getId(), loan.getMember().getId());
        history.record(loan, LoanEventType.EXPIRED, "Not collected in time");
        notifications.notifyMember(loan.getMember().getId(), NotificationType.HOLD_EXPIRED,
                "Your hold on \"%s\" expired because it was not collected in time.".formatted(loan.getBook().getTitle()),
                LoanService.MY_LOANS_LINK);
        holdQueue.serve(loan.getBook());
        return true;
    }

    @Transactional
    public void sendReminders(LocalDate today) {
        List<Loan> dueSoon = loans.findDueSoonNotYetNotified(today, today.plusDays(policy.dueSoonDays()));
        for (Loan loan : dueSoon) {
            notifications.notifyMember(loan.getMember().getId(), NotificationType.DUE_SOON,
                    "\"%s\" is due on %s.".formatted(loan.getBook().getTitle(), loan.getDueDate()), LoanService.MY_LOANS_LINK);
            loan.setDueSoonNotified(true);
        }
        List<Loan> overdue = loans.findOverdueNotYetNotified(today);
        for (Loan loan : overdue) {
            notifications.notifyMember(loan.getMember().getId(), NotificationType.OVERDUE,
                    "\"%s\" was due on %s. Please return it as soon as possible."
                            .formatted(loan.getBook().getTitle(), loan.getDueDate()), LoanService.MY_LOANS_LINK);
            loan.setOverdueNotified(true);
            loan.setDueSoonNotified(true);
        }
        log.info("Loan reminders for {}: {} due soon, {} overdue", today, dueSoon.size(), overdue.size());
    }
}
