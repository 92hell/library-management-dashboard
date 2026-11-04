package com.helmifr.library.account;

import com.helmifr.library.notification.channel.ChannelSettings;
import com.helmifr.library.notification.channel.Recipient;
import com.helmifr.library.notification.channel.RecipientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Emails the reset link. Customers get it at the email on their library card, librarians at their
 * account email (the same addresses as notifications). Needs the email channel configured.
 */
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final ChannelSettings settings;
    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final RecipientResolver recipients;

    /** @param mailSender null when no SMTP server is configured */
    public PasswordResetMailer(ChannelSettings settings, JavaMailSender mailSender, String baseUrl,
                               RecipientResolver recipients) {
        this.settings = settings;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.recipients = recipients;
    }

    /** Can links be sent at all? Without email there is no safe way to prove who is asking. */
    public boolean isAvailable() {
        return settings != null && settings.enabled() && mailSender != null;
    }

    public void send(PasswordResetRequested request) {
        Recipient to = recipients.find(request.accountId()).orElse(null);
        if (to == null || to.email() == null || to.email().isBlank()) {
            log.info("Password reset for account {} not sent: no email address", request.accountId());
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(settings.sender());
        mail.setTo(to.email());
        mail.setSubject("[Library] Reset your password");
        mail.setText("""
                Hi %s,

                Someone (hopefully you) asked to reset the password of your library account "%s".
                Open this link within 30 minutes to choose a new password:

                %s/reset-password?token=%s

                The link works once. If you did not ask for this, ignore this email: your password
                stays the same.

                --\s
                Your library
                """.formatted(to.displayName(), request.username(), baseUrl, request.token()));
        mailSender.send(mail);
        log.info("Password reset link sent for account {}", request.accountId());
    }
}
