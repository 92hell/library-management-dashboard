package com.helmifr.library.notification.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External notification channels, bound from {@code library.notifications.*}.
 *
 * @param baseUrl public address of the app, used to turn in-app links into clickable URLs
 */
@ConfigurationProperties(prefix = "library.notifications")
public record NotificationChannelProperties(String baseUrl, ChannelSettings email, ChannelSettings sms,
                                            ChannelSettings whatsapp) {}
