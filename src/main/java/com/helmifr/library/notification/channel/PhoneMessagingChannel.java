package com.helmifr.library.notification.channel;

import com.helmifr.library.common.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for phone-number channels (SMS, WhatsApp). Provider integrations are not written yet:
 * when enabled, these channels run in dry-run mode and log what they would send (number masked).
 * To integrate a provider, override {@link #deliver} with the HTTP call to settings.apiUrl()
 * using settings.apiKey(), e.g. Twilio Messaging API or the WhatsApp Business Cloud API.
 */
public abstract class PhoneMessagingChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PhoneMessagingChannel.class);

    protected final ChannelSettings settings;

    protected PhoneMessagingChannel(ChannelSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isEnabled() {
        return settings != null && settings.enabled();
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return recipient.phone() != null && !recipient.phone().isBlank();
    }

    @Override
    public final void send(Recipient recipient, OutgoingMessage message) {
        String text = message.link() == null ? message.text() : message.text() + " " + message.link();
        deliver(recipient.phone(), text);
    }

    /** Sends {@code text} to {@code phone}. Default: dry run (log only). */
    protected void deliver(String phone, String text) {
        log.info("[{} dry-run] provider={} to={} text=\"{}\"", name(), settings.provider(), PhoneNumbers.mask(phone), text);
    }
}
