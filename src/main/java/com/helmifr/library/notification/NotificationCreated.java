package com.helmifr.library.notification;

/** Published for every stored notification; external channels deliver it after commit. */
public record NotificationCreated(Long notificationId, Long accountId, NotificationType type, String message,
                                  String link) {}
