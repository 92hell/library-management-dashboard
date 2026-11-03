package com.helmifr.library.notification.channel;

/** Where a notification can be delivered. email and phone may be null. */
public record Recipient(Long accountId, String displayName, String email, String phone) {}
