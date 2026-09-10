package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

final class DatabaseSecurityActor {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode("test-password-123");

    private final JdbcTemplate jdbc;
    private final AuthIdentityService identities;

    DatabaseSecurityActor(JdbcTemplate jdbc, AuthIdentityService identities) {
        this.jdbc = jdbc;
        this.identities = identities;
    }

    Actor createAdministrator(String prefix) {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long userId = jdbc.queryForObject("""
                insert into sys_user(username,display_name,password_hash,status,administrator,must_change_password)
                values (?, 'Test administrator', ?, 'ENABLED', true, false)
                returning id
                """, Long.class, username, PASSWORD_HASH);
        var principal = identities.loadPrincipal(username);
        var details = new SystemUserDetails(
                principal, null, identities.loadAuthorities(principal), true, true);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities());
        return new Actor(userId, username, details, authentication);
    }

    void delete(Actor actor) {
        jdbc.update("delete from sys_user where id=?", actor.userId());
    }

    record Actor(long userId, String username, SystemUserDetails details, Authentication authentication) {
    }
}
