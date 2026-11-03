package com.helmifr.library.account;

import com.helmifr.library.account.AccountDtos.AccountResponse;
import com.helmifr.library.account.AccountDtos.EnabledRequest;
import com.helmifr.library.account.AccountDtos.NewLibrarianRequest;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Account administration for librarians. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final AccountService accountService;

    @GetMapping
    public PageResponse<AccountResponse> list(@RequestParam(required = false) Role role,
                                              @RequestParam(required = false) String q,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return accountService.search(role, q, pageable);
    }

    @PostMapping("/librarians")
    public ResponseEntity<AccountResponse> createLibrarian(@Valid @RequestBody NewLibrarianRequest request) {
        AccountResponse created = accountService.createLibrarian(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}/enabled")
    public AccountResponse setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return accountService.setEnabled(CurrentUser.id(), id, request.enabled());
    }
}
