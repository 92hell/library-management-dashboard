package com.helmifr.library.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.helmifr.library.notification.channel.ChannelSettings;
import com.helmifr.library.notification.channel.Recipient;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@DisplayName("Password reset email")
class PasswordResetMailTest {

    private static final ChannelSettings EMAIL = new ChannelSettings(true, "Library <no-reply@library.example>", null, null, null);
    private final JavaMailSender sender = mock(JavaMailSender.class);

    private PasswordResetMailer mailer(Optional<Recipient> recipient) {
        return new PasswordResetMailer(EMAIL, sender, "https://library.example/", accountId -> recipient);
    }

    @Test
    @DisplayName("is sent to the account's email with the link, the username and how long the link works")
    void content() {
        mailer(Optional.of(new Recipient(2L, "Siti", "siti@example.com", null)))
                .send(new PasswordResetRequested(2L, "siti", "abc_DEF-123"));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("siti@example.com");
        assertThat(mail.getValue().getSubject()).isEqualTo("[Library] Reset your password");
        assertThat(mail.getValue().getText()).contains(
                "Hi Siti", "siti", "https://library.example/reset-password?token=abc_DEF-123", "30 minutes",
                "If you did not ask for this");
    }

    @Test
    @DisplayName("is not sent when the account has no email address")
    void noAddress() {
        mailer(Optional.of(new Recipient(2L, "Siti", " ", null))).send(new PasswordResetRequested(2L, "siti", "t"));
        mailer(Optional.empty()).send(new PasswordResetRequested(2L, "siti", "t"));

        verifyNoInteractions(sender);
    }
}
