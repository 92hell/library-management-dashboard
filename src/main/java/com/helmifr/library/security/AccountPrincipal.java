package com.helmifr.library.security;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccount;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user as stored in the HTTP session: ids and a credential stamp, never entities.
 * The password hash is only present during authentication and is erased afterwards.
 */
public class AccountPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 2L;

    private final Long id;
    private final String username;
    private final Role role;
    private final Long memberId;
    private final boolean enabled;
    private final String credentialStamp;
    private String passwordHash;

    public AccountPrincipal(UserAccount account) {
        this.id = account.getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.role = account.getRole();
        this.memberId = account.getMember() == null ? null : account.getMember().getId();
        this.enabled = account.isEnabled();
        this.credentialStamp = stampOf(account);
    }

    /**
     * Changes whenever the password changes. A session whose stamp no longer matches the account
     * (see AccountSessionFilter) is ended - so a password change logs out other sessions.
     */
    public static String stampOf(UserAccount account) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(account.getPasswordHash().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

    public String getCredentialStamp() {
        return credentialStamp;
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

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
