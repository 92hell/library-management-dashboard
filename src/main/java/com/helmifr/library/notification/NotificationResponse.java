package com.helmifr.library.notification;

import java.time.Instant;

public record NotificationResponse(Long id, NotificationType type, String message, String link, boolean read,
                                   Instant createdAt) {
    /** With the message in the reader's language when the notification kept its template. */
    static NotificationResponse from(Notification n, String message) {
        return new NotificationResponse(n.getId(), n.getType(), message, n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
