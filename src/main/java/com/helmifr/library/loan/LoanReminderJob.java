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

    @Scheduled(cron = "${library.loans.reminder-cron}")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        expireUncollectedHolds(today);
        sendReminders(today);
    }

    /** Holds not collected by their pickup date expire; the copy goes to the next in the queue. */
    @Transactional
    public void expireUncollectedHolds(LocalDate today) {
        List<Loan> expired = loans.findUncollectedHolds(today);
        for (Loan loan : expired) {
            loanService.lockBook(loan.getBook().getId());
            loan.setStatus(LoanStatus.EXPIRED);
            loan.setReadyUntil(null);
            Audit.event("HOLD_EXPIRED", "loanId={} memberId={}", loan.getId(), loan.getMember().getId());
            notifications.notifyMember(loan.getMember().getId(), NotificationType.HOLD_EXPIRED,
                    "Your hold on \"%s\" expired because it was not collected in time.".formatted(loan.getBook().getTitle()),
                    LoanService.MY_LOANS_LINK);
            holdQueue.serve(loan.getBook());
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} uncollected hold(s) for {}", expired.size(), today);
        }
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
