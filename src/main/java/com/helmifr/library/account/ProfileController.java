package com.helmifr.library.account;

import com.helmifr.library.account.AccountDtos.PasswordChangeRequest;
import com.helmifr.library.account.AccountDtos.ProfileResponse;
import com.helmifr.library.account.AccountDtos.ProfileUpdateRequest;
import com.helmifr.library.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's own profile (any role). */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AccountService accountService;

    @GetMapping
    public ProfileResponse get() {
        return accountService.profile(CurrentUser.id());
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.updateProfile(CurrentUser.id(), request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        accountService.changePassword(CurrentUser.id(), request);
        return ResponseEntity.noContent().build();
    }
}
