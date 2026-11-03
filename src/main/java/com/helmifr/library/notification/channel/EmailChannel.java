package com.helmifr.library.notification.channel;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Email over SMTP. Needs library.notifications.email.enabled=true and an SMTP server in
 * spring.mail.* (host, port, username, password); without a server it stays off.
 */
public class EmailChannel implements NotificationChannel {

    private static final String SUBJECT_PREFIX = "[Library] ";

    private final ChannelSettings settings;
    private final JavaMailSender mailSender;

    /** @param mailSender null when no SMTP server is configured */
    public EmailChannel(ChannelSettings settings, JavaMailSender mailSender) {
        this.settings = settings;
        this.mailSender = mailSender;
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public boolean isEnabled() {
        return settings != null && settings.enabled() && mailSender != null;
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return recipient.email() != null && !recipient.email().isBlank();
    }

    @Override
    public void send(Recipient recipient, OutgoingMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(settings.sender());
        mail.setTo(recipient.email());
        mail.setSubject(SUBJECT_PREFIX + message.type().title());
        StringBuilder text = new StringBuilder("Hi ").append(recipient.displayName()).append(",\n\n")
                .append(message.text()).append("\n");
        if (message.link() != null) {
            text.append("\nOpen: ").append(message.link()).append("\n");
        }
        text.append("\n-- \nYour library");
        mail.setText(text.toString());
        mailSender.send(mail);
    }
}
