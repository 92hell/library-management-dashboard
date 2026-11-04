package com.helmifr.library.account;

import com.helmifr.library.notification.channel.NotificationChannelProperties;
import com.helmifr.library.notification.channel.RecipientResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration
public class PasswordResetMailConfig {

    @Bean
    PasswordResetMailer passwordResetMailer(NotificationChannelProperties props, ObjectProvider<JavaMailSender> mailSender,
                                            RecipientResolver recipients) {
        return new PasswordResetMailer(props.email(), mailSender.getIfAvailable(), props.baseUrl(), recipients);
    }

    /**
     * Sends the email after the request's transaction commits and on a background thread, so the
     * response comes back in the same time whether or not an account matched.
     */
    @Component
    static class Delivery {
        private final PasswordResetMailer mailer;

        Delivery(PasswordResetMailer mailer) {
            this.mailer = mailer;
        }

        @Async
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void deliver(PasswordResetRequested request) {
            mailer.send(request);
        }
    }
}
