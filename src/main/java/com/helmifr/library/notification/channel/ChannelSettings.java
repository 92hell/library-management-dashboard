package com.helmifr.library.notification.channel;

/**
 * Settings shared by external channels (library.notifications.email / sms / whatsapp).
 *
 * @param enabled  off by default
 * @param sender   email "From" address, or the sender number / id for SMS and WhatsApp
 * @param provider SMS/WhatsApp provider name, e.g. "twilio" or "meta-cloud-api" (unused for email)
 * @param apiUrl   provider endpoint (unused for email, which uses spring.mail.*)
 * @param apiKey   provider credential - pass it through an environment variable, never commit it
 */
public record ChannelSettings(boolean enabled, String sender, String provider, String apiUrl, String apiKey) {}
