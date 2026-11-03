package com.helmifr.library.notification;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.common.Messages;
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
    private final Messages messages;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Notifies one account. {@code template} is an English text with {0}-style values (see Messages):
     * the stored message and emails use English; the app shows it in the reader's language.
     */
    @Transactional
    public void notifyAccount(Long accountId, NotificationType type, String link, String template, Object... args) {
        String message = Messages.english(template, args);
        Notification n = new Notification();
        n.setUserId(accountId);
        n.setType(type);
        n.setMessage(message.length() > 500 ? message.substring(0, 497) + "..." : message);
        n.setLink(link);
        n.setMessageKey(template.length() > 500 ? null : template);
        n.setMessageArgs(argsJson(args));
        n = notifications.save(n);
        events.publishEvent(new NotificationCreated(n.getId(), accountId, type, n.getMessage(), link));
        log.debug("Notification {} queued for account {}", type, accountId);
    }

    /** Notifies the member's online account; walk-in members without an account are skipped. */
    @Transactional
    public void notifyMember(Long memberId, NotificationType type, String link, String template, Object... args) {
        accounts.findByMemberId(memberId)
                .filter(UserAccount::isEnabled)
                .ifPresentOrElse(
                        account -> notifyAccount(account.getId(), type, link, template, args),
                        () -> log.debug("Member {} has no active account; {} not delivered", memberId, type));
    }

    @Transactional
    public void notifyLibrarians(NotificationType type, String link, String template, Object... args) {
        accounts.findAllByRoleAndEnabledTrue(Role.LIBRARIAN)
                .forEach(librarian -> notifyAccount(librarian.getId(), type, link, template, args));
    }

    public PageResponse<NotificationResponse> list(Long accountId, Pageable pageable) {
        return PageResponse.of(notifications.findAllByUserIdOrderByIdDesc(accountId, pageable)
                .map(n -> NotificationResponse.from(n, localized(n))));
    }

    /** The message in the reader's language when the template was kept; the stored English otherwise. */
    private String localized(Notification n) {
        if (n.getMessageKey() == null) {
            return n.getMessage();
        }
        try {
            Object[] args = n.getMessageArgs() == null ? new Object[0] : JSON.readValue(n.getMessageArgs(), String[].class);
            return messages.get(n.getMessageKey(), args);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return n.getMessage();
        }
    }

    private static String argsJson(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            String json = JSON.writeValueAsString(java.util.Arrays.stream(args).map(a -> a == null ? "" : a.toString()).toList());
            return json.length() > 1000 ? null : json;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
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
