package com.helmifr.library.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.helmifr.library.notification.NotificationCreated;
import com.helmifr.library.notification.NotificationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Plain unit test: the dispatcher fans a notification out to the enabled external channels. */
@DisplayName("Notification dispatcher")
class NotificationDispatcherTest {

    static final Recipient SITI = new Recipient(2L, "Siti", "siti@example.com", "081234567890");
    static final NotificationCreated EVENT =
            new NotificationCreated(10L, 2L, NotificationType.DUE_SOON, "\"Saman\" is due tomorrow.", "/my/loans");

    /** Records what it was asked to send; can be told to fail. */
    static class FakeChannel implements NotificationChannel {
        final String name;
        final boolean enabled;
        final boolean failing;
        final List<String> sent = new ArrayList<>();

        FakeChannel(String name, boolean enabled, boolean failing) {
            this.name = name;
            this.enabled = enabled;
            this.failing = failing;
        }

        @Override public String name() { return name; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public boolean canReach(Recipient r) { return true; }

        @Override
        public void send(Recipient recipient, OutgoingMessage message) {
            if (failing) throw new IllegalStateException("provider down");
            sent.add(recipient.displayName() + ": " + message.text());
        }
    }

    @Test
    @DisplayName("sends through enabled channels only")
    void onlyEnabledChannels() {
        FakeChannel email = new FakeChannel("email", true, false);
        FakeChannel sms = new FakeChannel("sms", false, false);
        var dispatcher = new NotificationDispatcher(List.of(email, sms), id -> Optional.of(SITI), "https://library.example");

        dispatcher.dispatch(EVENT);

        assertThat(email.sent).containsExactly("Siti: \"Saman\" is due tomorrow.");
        assertThat(sms.sent).isEmpty();
    }

    @Test
    @DisplayName("one failing channel does not stop the others")
    void failureIsIsolated() {
        FakeChannel whatsapp = new FakeChannel("whatsapp", true, true);
        FakeChannel email = new FakeChannel("email", true, false);
        var dispatcher = new NotificationDispatcher(List.of(whatsapp, email), id -> Optional.of(SITI), "https://library.example");

        dispatcher.dispatch(EVENT);

        assertThat(email.sent).hasSize(1);
    }

    @Test
    @DisplayName("links are made absolute with the configured base URL")
    void absoluteLink() {
        List<OutgoingMessage> captured = new ArrayList<>();
        NotificationChannel capturing = new FakeChannel("email", true, false) {
            @Override
            public void send(Recipient recipient, OutgoingMessage message) {
                captured.add(message);
            }
        };
        new NotificationDispatcher(List.of(capturing), id -> Optional.of(SITI), "https://library.example/")
                .dispatch(EVENT);

        assertThat(captured.get(0).link()).isEqualTo("https://library.example/my/loans");
    }

    @Test
    @DisplayName("nothing is sent for an unknown or disabled account")
    void unknownRecipient() {
        FakeChannel email = new FakeChannel("email", true, false);
        new NotificationDispatcher(List.of(email), id -> Optional.empty(), "https://library.example").dispatch(EVENT);
        assertThat(email.sent).isEmpty();
    }
}
