package com.helmifr.library.security;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccount;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The signed-in user as stored in the HTTP session. Holds ids only, never entities. */
public class AccountPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final Long memberId;
    private final boolean enabled;

    public AccountPrincipal(UserAccount account) {
        this.id = account.getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.role = account.getRole();
        this.memberId = account.getMember() == null ? null : account.getMember().getId();
        this.enabled = account.isEnabled();
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    /** The customer's member record, or null for librarians. */
    public Long getMemberId() {
        return memberId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
