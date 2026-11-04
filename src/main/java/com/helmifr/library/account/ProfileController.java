package com.helmifr.library.account;

import com.helmifr.library.account.AccountDtos.PasswordChangeRequest;
import com.helmifr.library.account.AccountDtos.ProfileResponse;
import com.helmifr.library.account.AccountDtos.ProfileUpdateRequest;
import com.helmifr.library.security.AccountPrincipal;
import com.helmifr.library.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import jakarta.validation.Valid;
import com.helmifr.library.member.MemberCardDtos.PrintableCard;
import com.helmifr.library.member.MemberCards;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's own profile (any role). */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AccountService accountService;
    private final MemberCards cards;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping
    public ProfileResponse get() {
        return accountService.profile(CurrentUser.id());
    }

    /** The signed-in member's own library card, laid out for printing. */
    @GetMapping("/card")
    public PrintableCard card() {
        return cards.printable(accountService.memberId(CurrentUser.id()));
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.updateProfile(CurrentUser.id(), request);
    }

    /**
     * Changes the password. Other sessions of this account end (their credential stamp no longer
     * matches); this session is refreshed so the user stays signed in here.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UserAccount updated = accountService.changePassword(CurrentUser.id(), request);
        AccountPrincipal principal = new AccountPrincipal(updated);
        principal.eraseCredentials();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
