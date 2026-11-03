package com.helmifr.library.account;

import com.helmifr.library.account.AccountDtos.AccountResponse;
import com.helmifr.library.account.AccountDtos.NewLibrarianRequest;
import com.helmifr.library.account.AccountDtos.PasswordChangeRequest;
import com.helmifr.library.account.AccountDtos.ProfileResponse;
import com.helmifr.library.account.AccountDtos.ProfileUpdateRequest;
import com.helmifr.library.account.AccountDtos.RegisterRequest;
import com.helmifr.library.account.AccountDtos.SessionUser;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.PhoneNumbers;
import com.helmifr.library.common.SearchPatterns;
import com.helmifr.library.member.Member;
import com.helmifr.library.member.LibraryCardIds;
import com.helmifr.library.member.MemberRepository;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    static final String NO_MATCHING_CARD = "No library card matches this ID with your email or phone number";
    static final String CARD_EXISTS = "You already have a library card with us. Enter its ID to link it to this account.";

    private final UserAccountRepository accounts;
    private final MemberRepository members;
    private final LibraryCardIds libraryCardIds;
    private final PasswordEncoder passwordEncoder;

    // ---- Registration --------------------------------------------------------------------------

    /**
     * Creates a customer account. With a library card ID, the existing member record (e.g. a walk-in
     * member with loan history) is linked, after checking the date of birth; otherwise a new
     * member record is created from the registration details.
     */
    @Transactional
    public SessionUser register(RegisterRequest request) {
        requireFreeUsername(request.username());

        Member member;
        if (request.libraryCardId() != null && !request.libraryCardId().isBlank()) {
            member = members.findByLibraryCardIdIgnoreCaseAndDeletedFalse(request.libraryCardId().trim())
                    .filter(m -> accounts.findByMemberId(m.getId()).isEmpty())
                    .filter(m -> m.getEmail().equalsIgnoreCase(request.email().trim())
                            || PhoneNumbers.same(m.getPhone(), request.phone()))
                    .orElseThrow(() -> new FieldValidationException("libraryCardId", NO_MATCHING_CARD));
        } else {
            boolean existingCard = members.findUnlinkedByEmailOrPhoneContaining(
                            request.email().trim(), PhoneNumbers.lastDigits(request.phone(), 4)).stream()
                    .anyMatch(m -> m.getEmail().equalsIgnoreCase(request.email().trim())
                            || PhoneNumbers.same(m.getPhone(), request.phone()));
            if (existingCard) {
                throw new FieldValidationException("libraryCardId", CARD_EXISTS);
            }
            member = new Member();
            member.setName(request.name().trim());
            member.setDateOfBirth(request.dateOfBirth());
            member.setEmail(normalizeEmail(request.email()));
            member.setPhone(PhoneNumbers.normalize(request.phone()));
            member.setLibraryCardId(libraryCardIds.next());
            member = members.save(member);
        }

        UserAccount account = new UserAccount();
        account.setUsername(request.username().trim());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(Role.CUSTOMER);
        account.setDisplayName(request.name().trim());
        account.setEmail(normalizeEmail(request.email()));
        account.setMember(member);
        account = accounts.save(account);

        Audit.event("ACCOUNT_REGISTERED", "username={} libraryCardId={} linkedExistingCard={}",
                account.getUsername(), member.getLibraryCardId(), request.libraryCardId() != null);
        return SessionUser.of(account);
    }

    // ---- Own profile ---------------------------------------------------------------------------

    public ProfileResponse profile(Long accountId) {
        return ProfileResponse.of(require(accountId));
    }

    public SessionUser sessionUser(Long accountId) {
        return SessionUser.of(require(accountId));
    }

    /** Customers' name, email and phone are kept in sync with their member record. */
    @Transactional
    public ProfileResponse updateProfile(Long accountId, ProfileUpdateRequest request) {
        UserAccount account = require(accountId);
        account.setDisplayName(request.displayName().trim());
        account.setEmail(normalizeEmail(request.email()));
        Member member = account.getMember();
        if (member != null) {
            member.setName(account.getDisplayName());
            member.setEmail(account.getEmail());
            if (request.phone() != null && !request.phone().isBlank()) {
                member.setPhone(PhoneNumbers.normalize(request.phone()));
            }
        }
        Audit.event("PROFILE_UPDATED", "username={}", account.getUsername());
        return ProfileResponse.of(account);
    }

    @Transactional
    public void changePassword(Long accountId, PasswordChangeRequest request) {
        UserAccount account = require(accountId);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new FieldValidationException("currentPassword", "is not correct");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        Audit.event("PASSWORD_CHANGED", "username={}", account.getUsername());
    }

    // ---- Administration (librarians) -----------------------------------------------------------

    public PageResponse<AccountResponse> search(Role role, String q, Pageable pageable) {
        return PageResponse.of(accounts.search(role, SearchPatterns.contains(q), pageable).map(AccountResponse::of));
    }

    @Transactional
    public AccountResponse createLibrarian(NewLibrarianRequest request) {
        requireFreeUsername(request.username());
        UserAccount account = newLibrarian(request.username().trim(), request.password(), request.displayName().trim(),
                request.email() == null ? null : normalizeEmail(request.email()));
        Audit.event("LIBRARIAN_CREATED", "username={}", account.getUsername());
        return AccountResponse.of(account);
    }

    @Transactional
    public AccountResponse setEnabled(Long actorId, Long accountId, boolean enabled) {
        if (Objects.equals(actorId, accountId)) {
            throw new FieldValidationException("enabled", "You cannot disable your own account");
        }
        UserAccount account = require(accountId);
        account.setEnabled(enabled);
        Audit.event(enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", "username={}", account.getUsername());
        return AccountResponse.of(account);
    }

    /** Used by startup bootstrap and demo seeding; skips validation rules meant for user input. */
    @Transactional
    public UserAccount newLibrarian(String username, String rawPassword, String displayName, String email) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setRole(Role.LIBRARIAN);
        account.setDisplayName(displayName);
        account.setEmail(email);
        return accounts.save(account);
    }

    private UserAccount require(Long id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("Account", id));
    }

    private void requireFreeUsername(String username) {
        if (accounts.existsByUsernameIgnoreCase(username.trim())) {
            throw new FieldValidationException("username", "is already taken");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
