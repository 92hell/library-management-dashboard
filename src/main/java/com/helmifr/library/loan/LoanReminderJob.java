package com.helmifr.library.loan;

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

    @Scheduled(cron = "${library.loans.reminder-cron}")
    public void run() {
        sendReminders(LocalDate.now(clock));
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
