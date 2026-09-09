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
    void administratorAuthorityGrantsEveryRegisteredPermission() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin", "credentials", List.of(new SimpleGrantedAuthority("system.administrator")));

        assertThat(AuthorityRules.hasAuthority(() -> authentication, "purchase.manage").isGranted()).isTrue();
    }
}
