package com.helmifr.library.notification.channel;

import com.helmifr.library.notification.NotificationCreated;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Wires the external channels from configuration and delivers notifications after commit. */
@Configuration
public class NotificationChannelConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelConfig.class);

    @Bean
    NotificationDispatcher notificationDispatcher(NotificationChannelProperties props,
                                                  ObjectProvider<JavaMailSender> mailSender,
                                                  RecipientResolver recipients) {
        List<NotificationChannel> channels = List.of(
                new EmailChannel(props.email(), mailSender.getIfAvailable()),
                new SmsChannel(props.sms()),
                new WhatsAppChannel(props.whatsapp()));
        channels.forEach(c -> log.info("Notification channel {}: {}", c.name(), c.isEnabled() ? "enabled" : "disabled"));
        if (props.email() != null && props.email().enabled() && mailSender.getIfAvailable() == null) {
            log.warn("Email notifications are enabled but no SMTP server is configured (spring.mail.host)");
        }
        return new NotificationDispatcher(channels, recipients, props.baseUrl());
    }

    /**
     * Runs after the transaction that stored the notification commits (so a rollback never sends a
     * message) and on a background thread (so a slow SMTP server never slows down a request).
     */
    @Component
    static class AfterCommitDelivery {
        private final NotificationDispatcher dispatcher;

        AfterCommitDelivery(NotificationDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Async
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void deliver(NotificationCreated event) {
            dispatcher.dispatch(event);
        }
    }
}
