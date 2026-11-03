package com.helmifr.library.notification;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications. Delivery is a database row the UI polls for; an email or push channel
 * can be added later behind {@link #notifyAccount}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final UserAccountRepository accounts;

    @Transactional
    public void notifyAccount(Long accountId, NotificationType type, String message, String link) {
        Notification n = new Notification();
        n.setUserId(accountId);
        n.setType(type);
        n.setMessage(message.length() > 500 ? message.substring(0, 497) + "..." : message);
        n.setLink(link);
        notifications.save(n);
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
