package com.helmifr.library.account;

import com.helmifr.library.common.PhoneNumbers;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request/response shapes for accounts, registration and profiles. */
public final class AccountDtos {
    private AccountDtos() {}

    static final String USERNAME_REGEX = "^[A-Za-z0-9._-]{3,50}$";
    static final String PHONE_REGEX = PhoneNumbers.PATTERN;

    /** Self-registration. With libraryCardId, an existing (walk-in) member record is linked instead of created. */
    public record RegisterRequest(
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "{library.validation.username}") String username,
            @NotBlank @Size(min = 10, max = 200) String password,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "{library.validation.phone}") String phone,
            @NotNull @Past LocalDate dateOfBirth,
            @Size(max = 20) String libraryCardId) {}

    public record NewLibrarianRequest(
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "{library.validation.username}") String username,
            @NotBlank @Size(min = 10, max = 200) String password,
            @NotBlank @Size(max = 255) String displayName,
            @Email @Size(max = 255) String email) {}

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = PHONE_REGEX, message = "{library.validation.phone}") String phone) {}

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 200) String newPassword) {}

    public record EnabledRequest(@NotNull Boolean enabled) {}

    /** The signed-in user, as returned by /api/auth/me, login and registration. */
    /** @param demoAccount a public demo login (restricted; the UI hides what it cannot do) */
    public record SessionUser(Long id, String username, String displayName, Role role, Long memberId,
                              boolean demoAccount) {
        static SessionUser of(UserAccount a, boolean demoAccount) {
            return new SessionUser(a.getId(), a.getUsername(), a.getDisplayName(), a.getRole(),
                    a.getMember() == null ? null : a.getMember().getId(), demoAccount);
        }
    }

    /** The member's own card; {@code cardExpiresOn} null = it does not expire. */
    public record MemberCard(Long id, String libraryCardId, String name, String email, String phone,
                             LocalDate dateOfBirth, boolean active, LocalDate cardExpiresOn, boolean cardExpired) {}

    public record ProfileResponse(Long id, String username, Role role, String displayName, String email,
                                  MemberCard member) {
        static ProfileResponse of(UserAccount a, LocalDate today) {
            var m = a.getMember();
            MemberCard card = m == null ? null : new MemberCard(m.getId(), m.getLibraryCardId(), m.getName(),
                    m.getEmail(), m.getPhone(), m.getDateOfBirth(), m.isActive(), m.getCardExpiresOn(),
                    m.cardExpiredOn(today));
            return new ProfileResponse(a.getId(), a.getUsername(), a.getRole(), a.getDisplayName(), a.getEmail(), card);
        }
    }

    public record AccountResponse(Long id, String username, String displayName, String email, Role role,
                                  boolean enabled, Long memberId) {
        static AccountResponse of(UserAccount a) {
            return new AccountResponse(a.getId(), a.getUsername(), a.getDisplayName(), a.getEmail(), a.getRole(),
                    a.isEnabled(), a.getMember() == null ? null : a.getMember().getId());
        }
    }
}
