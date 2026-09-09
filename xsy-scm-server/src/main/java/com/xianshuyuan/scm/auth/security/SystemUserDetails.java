package com.xianshuyuan.scm.auth.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public final class SystemUserDetails implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthenticatedUser user;
    private final List<? extends GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private String password;

    public SystemUserDetails(
            AuthenticatedUser user,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean enabled,
            boolean accountNonLocked
    ) {
        this.user = user;
        this.password = password;
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    public AuthenticatedUser getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
