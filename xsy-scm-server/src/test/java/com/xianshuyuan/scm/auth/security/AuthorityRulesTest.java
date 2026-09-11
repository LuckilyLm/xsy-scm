package com.xianshuyuan.scm.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorityRulesTest {

    @Test
    void grantsExactAuthority() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user", "credentials", List.of(new SimpleGrantedAuthority("product.read")));

        assertThat(AuthorityRules.hasAuthority(() -> authentication, "product.read").isGranted()).isTrue();
        assertThat(AuthorityRules.hasAuthority(() -> authentication, "product.manage").isGranted()).isFalse();
    }

    @Test
    void administratorIdentityGrantsEveryRegisteredPermission() {
        var principal = new AuthenticatedUser(1L, "admin", "Admin", 0L, true, false, List.of());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("system.administrator")));

        assertThat(AuthorityRules.hasAuthority(() -> authentication, "purchase.manage").isGranted()).isTrue();
    }

    @Test
    void administratorPermissionAloneDoesNotCreateAdministratorIdentity() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "ordinary", null, List.of(new SimpleGrantedAuthority("system.administrator")));

        assertThat(AuthorityRules.isAdministrator(authentication)).isFalse();
        assertThat(AuthorityRules.hasAuthority(() -> authentication, "purchase.manage").isGranted()).isFalse();
    }

    @Test
    void treatsMarketingPermissionsAsReservedBusinessAuthorities() {
        assertThat(AuthorityRules.isReservedPermission("marketing.read", false)).isTrue();
        assertThat(AuthorityRules.isReservedPermission("marketing.manage", false)).isTrue();
    }
}
