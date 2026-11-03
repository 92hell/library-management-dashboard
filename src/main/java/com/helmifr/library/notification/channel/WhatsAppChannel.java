package com.helmifr.library.notification.channel;

/** WhatsApp via a provider configured in library.notifications.whatsapp.* (dry run until integrated). */
public class WhatsAppChannel extends PhoneMessagingChannel {
    public WhatsAppChannel(ChannelSettings settings) {
        super(settings);
    }

    @Override
    public String name() {
        return "whatsapp";
    }
}
