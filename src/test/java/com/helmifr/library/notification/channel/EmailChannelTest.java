package com.helmifr.library.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.helmifr.library.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@DisplayName("Email channel (SMTP)")
class EmailChannelTest {

    private static final ChannelSettings SETTINGS = new ChannelSettings(true, "Library <no-reply@library.example>", null, null, null);

    @Test
    @DisplayName("sends a plain-text email with subject, message and link")
    void sendsEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailChannel channel = new EmailChannel(SETTINGS, sender);

        channel.send(new Recipient(2L, "Siti", "siti@example.com", null),
                new OutgoingMessage(NotificationType.OVERDUE, "\"Saman\" is overdue.", "https://library.example/my/loans"));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("siti@example.com");
        assertThat(mail.getValue().getFrom()).isEqualTo("Library <no-reply@library.example>");
        assertThat(mail.getValue().getSubject()).isEqualTo("[Library] Overdue book");
        assertThat(mail.getValue().getText()).contains("Hi Siti", "\"Saman\" is overdue.", "https://library.example/my/loans");
    }

    @Test
    @DisplayName("is off without an SMTP server, and cannot reach recipients without an email address")
    void enablement() {
        assertThat(new EmailChannel(SETTINGS, null).isEnabled()).isFalse();
        EmailChannel channel = new EmailChannel(SETTINGS, mock(JavaMailSender.class));
        assertThat(channel.isEnabled()).isTrue();
        assertThat(channel.canReach(new Recipient(2L, "Siti", null, "0812"))).isFalse();
    }
}
