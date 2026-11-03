package com.helmifr.library.notification.channel;

import com.helmifr.library.notification.NotificationCreated;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends a notification through every enabled external channel that can reach the recipient. */
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> channels;
    private final RecipientResolver recipients;
    private final String baseUrl;

    public NotificationDispatcher(List<NotificationChannel> channels, RecipientResolver recipients, String baseUrl) {
        this.channels = List.copyOf(channels);
        this.recipients = recipients;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public boolean hasEnabledChannels() {
        return channels.stream().anyMatch(NotificationChannel::isEnabled);
    }

    public void dispatch(NotificationCreated event) {
        if (!hasEnabledChannels()) {
            return;
        }
        recipients.find(event.accountId()).ifPresentOrElse(recipient -> {
            OutgoingMessage message = new OutgoingMessage(event.type(), event.message(), absolute(event.link()));
            for (NotificationChannel channel : channels) {
                if (!channel.isEnabled() || !channel.canReach(recipient)) {
                    continue;
                }
                try {
                    channel.send(recipient, message);
                    log.info("Notification {} ({}) sent via {} to account {}", event.notificationId(), event.type(),
                            channel.name(), event.accountId());
                } catch (RuntimeException e) {
                    log.warn("Notification {} via {} to account {} failed: {}", event.notificationId(), channel.name(),
                            event.accountId(), e.toString());
                }
            }
        }, () -> log.debug("Account {} not reachable; notification {} stays in-app only",
                event.accountId(), event.notificationId()));
    }

    private String absolute(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        return link.startsWith("http") ? link : baseUrl + (link.startsWith("/") ? link : "/" + link);
    }
}
