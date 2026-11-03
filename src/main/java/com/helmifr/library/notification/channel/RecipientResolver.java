package com.helmifr.library.notification.channel;

import java.util.Optional;

/** Looks up the contact details of an account; empty for unknown or disabled accounts. */
@FunctionalInterface
public interface RecipientResolver {
    Optional<Recipient> find(Long accountId);
}
