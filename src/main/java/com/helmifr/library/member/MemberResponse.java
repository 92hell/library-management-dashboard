package com.helmifr.library.member;

import java.time.LocalDate;

/**
 * Member as seen by librarians.
 *
 * @param cardExpiresOn   last day the card can be used; null = it does not expire
 * @param cardExpired     whether the card has expired (the member cannot borrow until it is renewed)
 * @param accountUsername the linked online account, or null for walk-in members
 */
public record MemberResponse(Long id, String libraryCardId, String name, LocalDate dateOfBirth, String email,
                             String phone, boolean active, LocalDate cardExpiresOn, boolean cardExpired,
                             String accountUsername) {

    static MemberResponse from(Member m, String accountUsername, LocalDate today) {
        return new MemberResponse(m.getId(), m.getLibraryCardId(), m.getName(), m.getDateOfBirth(), m.getEmail(),
                m.getPhone(), m.isActive(), m.getCardExpiresOn(), m.cardExpiredOn(today), accountUsername);
    }
}
