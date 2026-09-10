package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "xsy.auth.login.failure-threshold=3",
        "xsy.auth.login.lock-duration=2m"
})
class LoginLockoutIT extends IsolatedUserDatabase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired FindByIndexNameSessionRepository sessionsRepository;
    @Autowired AuthIdentityService identities;
    @MockitoSpyBean UserSessionService sessions;

    @Test
    void successfulLoginPersistsSerializableSecurityContextAndRestoresOnNextRequest() throws Exception {
        String username = createUser("restore", "ENABLED", false);
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", "correct-password"))))
                .andExpect(status().isOk()).andReturn();
        var cookie = login.getResponse().getCookie("XSY_SESSION");
        assertThat(cookie).isNotNull();
        String sessionId = new String(java.util.Base64.getDecoder().decode(cookie.getValue()), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(sessionsRepository.findById(sessionId)).isNotNull();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username));
    }
    @Test
    void countsFailuresLocksAtThresholdRejectsCorrectPasswordAndSuccessClears() throws Exception {
        String username = createUser("lock", "ENABLED", false);
        for (int i = 1; i < 3; i++) {
            failedLogin(username, "wrong");
            assertThat(count(username)).isEqualTo(i);
            assertThat(lockedUntil(username)).isNull();
            assertThat(authVersion(username)).isZero();
        }
        java.time.OffsetDateTime beforeLock = java.time.OffsetDateTime.now();
        failedLogin(username, "wrong");
        assertThat(count(username)).isEqualTo(3);
        assertThat(lockedUntil(username)).isBetween(beforeLock.plusMinutes(1).plusSeconds(50), beforeLock.plusMinutes(2).plusSeconds(10));
        assertThat(authVersion(username)).isEqualTo(1L);
        failedLogin(username, "correct-password");
        assertThat(count(username)).isEqualTo(3);
        assertThat(authVersion(username)).isEqualTo(1L);

        String clears = createUser("clear", "ENABLED", false);
        failedLogin(clears, "wrong");
        successfulLogin(clears);
        assertThat(count(clears)).isZero();
        assertThat(lockedUntil(clears)).isNull();
        assertThat(jdbc.queryForObject("select last_login_at is not null from sys_user where username=?", Boolean.class, clears)).isTrue();

        String expired = createUser("expired", "ENABLED", false);
        jdbc.update("update sys_user set failed_login_count=3,locked_until=current_timestamp-interval '1 second',auth_version=1 where username=?", expired);
        failedLogin(expired, "wrong");
        assertThat(count(expired)).isEqualTo(1);
        assertThat(lockedUntil(expired)).isNull();
        assertThat(authVersion(expired)).isEqualTo(1L);
        successfulLogin(expired);
        assertThat(count(expired)).isZero();
        assertThat(lockedUntil(expired)).isNull();
    }

    @Test
    void disabledDeletedAndUnknownAreGenericAndDoNotMutateRows() throws Exception {
        String disabled = createUser("disabled", "DISABLED", false);
        String deleted = createUser("deleted", "ENABLED", true);
        failedLogin(disabled, "wrong");
        failedLogin(deleted, "wrong");
        failedLogin("missing-" + suffix(), "wrong");
        assertThat(count(disabled)).isZero();
        assertThat(countIncludingDeleted(deleted)).isZero();
    }

    @Test
    void authenticationFailureStateRollsBackWithTransaction() {
        String username = createUser("rollback", "ENABLED", false);
        org.springframework.transaction.support.TransactionTemplate template =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            try { failedLogin(username, "wrong"); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            assertThat(count(username)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(count(username)).isZero();
        assertThat(authVersion(username)).isZero();
    }

    @Test
    void thresholdLockCommitsVersionAndStaleJdbcSessionIsRejectedWhenCleanupFails() throws Exception {
        String username = createUser("session", "ENABLED", false);
        var principal = identities.loadPrincipal(username);
        var details = new SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
        Session stored = (Session) sessionsRepository.createSession();
        stored.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        stored.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
        sessionsRepository.save(stored);
        var cookie = new jakarta.servlet.http.Cookie("XSY_SESSION", java.util.Base64.getEncoder().encodeToString(stored.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me").cookie(cookie))
                    .andExpect(status().isOk());
            doThrow(new IllegalStateException("Simulated cleanup failure")).when(sessions).invalidateAll(username);
            failedLogin(username, "wrong");
            failedLogin(username, "wrong");
            failedLogin(username, "wrong");
            verify(sessions).invalidateAll(username);
            assertThat(count(username)).isEqualTo(3);
            assertThat(authVersion(username)).isEqualTo(1L);
            assertThat(sessionsRepository.findById(stored.getId())).isNotNull();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me").cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40103));
        } finally {
            sessionsRepository.deleteById(stored.getId());
        }
    }

    @Test
    void concurrentFailuresDoNotLoseIncrementsAndLock() throws Exception {
        String username = createUser("race", "ENABLED", false);
        int attempts = 3;
        var ready = new CountDownLatch(attempts);
        var start = new CountDownLatch(1);
        var failures = new ArrayList<Throwable>();
        try (var pool = Executors.newFixedThreadPool(attempts)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                try { start.await(); failedLogin(username, "wrong"); }
                catch (Throwable failure) { synchronized (failures) { failures.add(failure); } }
            }));
            ready.await(); start.countDown();
            for (var future : futures) future.get();
        }
        assertThat(failures).isEmpty();
        assertThat(count(username)).isEqualTo(attempts);
        assertThat(lockedUntil(username)).isNotNull();
    }

    private void failedLogin(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("username", username, "password", password))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101))
            .andExpect(jsonPath("$.message").value("用户名或密码错误"))
            .andExpect(content().json("{\"code\":40101,\"message\":\"用户名或密码错误\"}", true));
    }

    private void successfulLogin(String username) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("username", username, "password", "correct-password"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    private String createUser(String prefix, String status, boolean deleted) {
        String username = prefix + "-" + suffix();
        jdbc.update("insert into sys_user(username,display_name,password_hash,status,administrator,must_change_password,auth_version,failed_login_count,version,deleted) values(?,?,?, ?,false,false,0,0,0,?)",
                username, "Login Test", encoder.encode("correct-password"), status, deleted);
        return username;
    }

    private int count(String username) { return jdbc.queryForObject("select failed_login_count from sys_user where username=? and deleted=false", Integer.class, username); }
    private int countIncludingDeleted(String username) { return jdbc.queryForObject("select failed_login_count from sys_user where username=?", Integer.class, username); }
    private long authVersion(String username) { return jdbc.queryForObject("select auth_version from sys_user where username=?", Long.class, username); }
    private java.time.OffsetDateTime lockedUntil(String username) { return jdbc.queryForObject("select locked_until from sys_user where username=?", java.time.OffsetDateTime.class, username); }
    private String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
