package com.helmifr.library.notification.channel;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Customers are reached at their library card's email/phone; librarians at their account email. */
@Component
@RequiredArgsConstructor
public class AccountRecipientResolver implements RecipientResolver {

    private final UserAccountRepository accounts;

    @Override
    @Transactional(readOnly = true)
    public Optional<Recipient> find(Long accountId) {
        return accounts.findById(accountId)
                .filter(UserAccount::isEnabled)
                .map(a -> a.getMember() == null
                        ? new Recipient(a.getId(), a.getDisplayName(), a.getEmail(), null)
                        : new Recipient(a.getId(), a.getDisplayName(), a.getMember().getEmail(), a.getMember().getPhone()));
    }
}
