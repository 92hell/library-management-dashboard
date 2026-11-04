package com.helmifr.library.member;

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
 * Daily job that reminds members whose library card expires soon (30 days by default). Each expiry
 * date is reminded once (recorded on the member), so re-running is harmless, and a renewal - which
 * moves the date - brings a new reminder before the new date. Members without an online account
 * are skipped by the notifications; the desk sees their expiry date on the member page.
 */
@Component
@RequiredArgsConstructor
public class MemberCardReminderJob {

    static final String PROFILE_LINK = "/profile";
    private static final Logger log = LoggerFactory.getLogger(MemberCardReminderJob.class);

    private final MemberRepository members;
    private final NotificationService notifications;
    private final MemberCardPolicy policy;
    private final Clock clock;

    @Scheduled(cron = "${library.members.reminder-cron}")
    public void run() {
        remind(LocalDate.now(clock));
    }

    /** Sends the reminders due on {@code today}; returns how many members were reminded. */
    @Transactional
    public int remind(LocalDate today) {
        if (policy.cardReminderDays() <= 0) {
            return 0;
        }
        List<Member> expiring = members.findCardsExpiringUnreminded(today, today.plusDays(policy.cardReminderDays()));
        for (Member member : expiring) {
            notifications.notifyMember(member.getId(), NotificationType.CARD_EXPIRING, PROFILE_LINK,
                    "Your library card {0} expires on {1}. Renew it at the desk to keep borrowing.",
                    member.getLibraryCardId(), member.getCardExpiresOn());
            member.setCardReminderSentFor(member.getCardExpiresOn());
        }
        if (!expiring.isEmpty()) {
            log.info("Library card reminders for {}: {}", today, expiring.size());
        }
        return expiring.size();
    }
}
