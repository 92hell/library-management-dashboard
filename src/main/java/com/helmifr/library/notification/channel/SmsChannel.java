package com.helmifr.library.notification.channel;

/** SMS via a provider configured in library.notifications.sms.* (dry run until integrated). */
public class SmsChannel extends PhoneMessagingChannel {
    public SmsChannel(ChannelSettings settings) {
        super(settings);
    }

    @Override
    public String name() {
        return "sms";
    }
}
