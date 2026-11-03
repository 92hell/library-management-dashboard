package com.helmifr.library.member;

import java.time.LocalDate;

/**
 * Member as seen by librarians.
 *
 * @param accountUsername the linked online account, or null for walk-in members
 */
public record MemberResponse(Long id, String memberCode, String name, LocalDate dateOfBirth, String email,
                             String phone, boolean active, String accountUsername) {

    static MemberResponse from(Member m, String accountUsername) {
        return new MemberResponse(m.getId(), m.getMemberCode(), m.getName(), m.getDateOfBirth(), m.getEmail(),
                m.getPhone(), m.isActive(), accountUsername);
    }
}
