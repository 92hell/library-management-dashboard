package com.helmifr.library.notification.channel;

/**
 * An external delivery channel (email, SMS, WhatsApp, ...). The in-app inbox is not a channel:
 * it is always written. Implementations must be safe to call outside a transaction.
 */
public interface NotificationChannel {

    /** Short name for logs, e.g. "email". */
    String name();

    /** Switched on in configuration and able to work (e.g. an SMTP server is configured). */
    boolean isEnabled();

    /** Has the contact detail this channel needs (an email address, a phone number). */
    boolean canReach(Recipient recipient);

    /** Delivers the message; throws on failure (the dispatcher logs it and carries on). */
    void send(Recipient recipient, OutgoingMessage message);
}
