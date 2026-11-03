package com.helmifr.library.notification.channel;

import com.helmifr.library.notification.NotificationType;

/** A notification ready for an external channel; {@code link} is an absolute URL or null. */
public record OutgoingMessage(NotificationType type, String text, String link) {}
