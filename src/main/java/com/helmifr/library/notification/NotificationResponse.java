package com.helmifr.library.notification;

import java.time.Instant;

public record NotificationResponse(Long id, NotificationType type, String message, String link, boolean read,
                                   Instant createdAt) {
    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
