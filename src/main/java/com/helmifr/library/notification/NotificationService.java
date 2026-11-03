package com.helmifr.library.notification;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications. Each one is stored for the in-app inbox (the UI polls it) and published as a
 * {@link NotificationCreated} event, which the external channels (email, SMS, WhatsApp) pick up
 * after the transaction commits - see {@code notification.channel}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final UserAccountRepository accounts;
    private final ApplicationEventPublisher events;

    @Transactional
    public void notifyAccount(Long accountId, NotificationType type, String message, String link) {
        Notification n = new Notification();
        n.setUserId(accountId);
        n.setType(type);
        n.setMessage(message.length() > 500 ? message.substring(0, 497) + "..." : message);
        n.setLink(link);
        n = notifications.save(n);
        events.publishEvent(new NotificationCreated(n.getId(), accountId, type, n.getMessage(), link));
        log.debug("Notification {} queued for account {}", type, accountId);
    }

    /** Notifies the member's online account; walk-in members without an account are skipped. */
    @Transactional
    public void notifyMember(Long memberId, NotificationType type, String message, String link) {
        accounts.findByMemberId(memberId)
                .filter(UserAccount::isEnabled)
                .ifPresentOrElse(
                        account -> notifyAccount(account.getId(), type, message, link),
                        () -> log.debug("Member {} has no active account; {} not delivered", memberId, type));
    }

    @Transactional
    public void notifyLibrarians(NotificationType type, String message, String link) {
        accounts.findAllByRoleAndEnabledTrue(Role.LIBRARIAN)
                .forEach(librarian -> notifyAccount(librarian.getId(), type, message, link));
    }

    public PageResponse<NotificationResponse> list(Long accountId, Pageable pageable) {
        return PageResponse.of(notifications.findAllByUserIdOrderByIdDesc(accountId, pageable)
                .map(NotificationResponse::from));
    }

    public long unreadCount(Long accountId) {
        return notifications.countByUserIdAndReadFalse(accountId);
    }

    @Transactional
    public void markRead(Long accountId, Long notificationId) {
        notifications.findByIdAndUserId(notificationId, accountId)
                .orElseThrow(() -> new NotFoundException("Notification", notificationId))
                .setRead(true);
    }

    @Transactional
    public void markAllRead(Long accountId) {
        notifications.markAllRead(accountId);
    }
}
