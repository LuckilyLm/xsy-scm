package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.AuthErrorCodes;
import com.xianshuyuan.scm.auth.dto.ChangePasswordRequest;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.PasswordManagementService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.system.dto.ResetPasswordRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class PasswordManagementHttpIT extends IsolatedUserDatabase {
    private static final String CURRENT = "Current-Password-123!";
    private static final String NEXT = "Changed-Password-456!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthIdentityService identities;
    @Autowired PasswordManagementService passwords;
    @Autowired FindByIndexNameSessionRepository repository;
    @MockitoSpyBean UserSessionService sessions;

    @Test void ordinaryUserCanReadTheirCurrentVersionWithoutSystemPermissions() throws Exception {
        long id = account(false);
        jdbc.update("update sys_user set version=4 where id=?", id);
        mvc.perform(get("/api/auth/me").with(user(details(id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(4));
    }

    @Test void validHttpChangeUpdatesPasswordAndRejectsTheSameJdbcCookieDespiteCleanupFailure() throws Exception {
        long id = account(false);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(id));
        Session stored = repository.createSession();
        stored.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        stored.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username(id));
        repository.save(stored);
        Cookie cookie = new Cookie("XSY_SESSION", Base64.getEncoder().encodeToString(stored.getId().getBytes(StandardCharsets.UTF_8)));
        try {
            mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
            doThrow(new IllegalStateException("Simulated cleanup failure")).when(sessions).invalidateAll(username(id));
            mvc.perform(post("/api/auth/change-password").cookie(cookie).with(csrf())
                    .contentType("application/json").content(changeBody(0)))
                    .andExpect(status().isOk());
            assertThat(encoder.matches(NEXT, hash(id))).isTrue();
            assertThat(encoder.matches(CURRENT, hash(id))).isFalse();
            assertThat(repository.findById(stored.getId())).isNotNull();
            mvc.perform(get("/api/auth/me").cookie(cookie))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40103));
            assertThat(jdbc.queryForObject("select actor_user_id from sys_operation_log where operation_code='USER_PASSWORD_CHANGE' and target_id=?", Long.class, Long.toString(id))).isEqualTo(id);
            String audit = jdbc.queryForObject("select coalesce(before_data::text,'') || coalesce(after_data::text,'') from sys_operation_log where operation_code='USER_PASSWORD_CHANGE' and target_id=?", String.class, Long.toString(id));
            assertThat(audit.contains(CURRENT) || audit.contains(NEXT) || audit.contains(hash(id))).isFalse();
        } finally { repository.deleteById(stored.getId()); }
    }

    @Test void httpPolicyRejectsShortPasswordWithoutMutatingAccount() throws Exception {
        long id = account(false);
        mvc.perform(post("/api/auth/change-password").with(user(details(id))).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword", CURRENT, "newPassword", "Ab1!", "version", 0))))
                .andExpect(status().isBadRequest());
        assertUnchanged(id);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void expiredPrincipalCannotWriteAfterPassingTheRequestFilter(boolean reset) {
        long actor = account(reset), target = reset ? account(false) : actor;
        Authentication stale = authentication(actor);
        jdbc.update("update sys_user set auth_version=auth_version+1 where id=?", actor);
        assertThatThrownBy(() -> {
            if (reset) passwords.reset(target, new ResetPasswordRequest(NEXT, 0), stale);
            else passwords.changeOwn(new ChangePasswordRequest(CURRENT, NEXT, 0), stale);
        }).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(AuthErrorCodes.SESSION_INVALID);
        assertThat(encoder.matches(CURRENT, hash(target))).isTrue();
        assertThat(jdbc.queryForObject("select version from sys_user where id=?", Integer.class, target)).isZero();
    }

    @Test void unauthenticatedPrincipalCannotChangePasswordDirectly() {
        long id = account(false);
        Authentication actor = UsernamePasswordAuthenticationToken.unauthenticated(details(id), null);
        assertThatThrownBy(() -> passwords.changeOwn(new ChangePasswordRequest(CURRENT, NEXT, 0), actor))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCodes.LOGIN_REQUIRED);
        assertUnchanged(id);
    }

    @Test void resetHttpEndpointEnforcesAuthenticationCsrfAndDatabaseAdministrator() throws Exception {
        long admin = account(true), target = account(false), ordinary = account(false);
        String path = "/api/system/users/" + target + "/reset-password";
        String body = json.writeValueAsString(Map.of("newPassword", NEXT, "version", 0));
        mvc.perform(post(path).with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user(details(admin))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(details(ordinary))).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(details(admin))).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        assertThat(encoder.matches(NEXT, hash(target))).isTrue();
        assertThat(jdbc.queryForObject("select must_change_password from sys_user where id=?", Boolean.class, target)).isTrue();
    }

    private String changeBody(int version) throws Exception {
        return json.writeValueAsString(Map.of("currentPassword", CURRENT, "newPassword", NEXT, "version", version));
    }

    @Test void resetCredentialCanOnlyAccessPasswordRecoveryUntilChanged() throws Exception {
        long admin = account(true), target = account(true);
        passwords.reset(target, new ResetPasswordRequest(NEXT, 0), authentication(admin));
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username(target), "password", NEXT))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(true)).andReturn();
        Cookie cookie = login.getResponse().getCookie("XSY_SESSION");
        assertThat(cookie != null).isTrue();
        mvc.perform(get("/api/system/roles").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(get("/api/auth/csrf").cookie(cookie)).andExpect(status().isOk());
        String permanent = "Permanent-Password-789!";
        mvc.perform(post("/api/auth/change-password").cookie(cookie).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("currentPassword", NEXT, "newPassword", permanent, "version", 1))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username(target), "password", NEXT))))
                .andExpect(status().isUnauthorized());
        var renewed = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username(target), "password", permanent))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(false)).andReturn();
        mvc.perform(get("/api/system/roles").cookie(renewed.getResponse().getCookie("XSY_SESSION"))).andExpect(status().isOk());
    }
    private long account(boolean administrator) {
        return jdbc.queryForObject("insert into sys_user(username,display_name,password_hash,administrator,must_change_password) values(?,'Password HTTP',?,?,false) returning id", Long.class,
                "pwh" + UUID.randomUUID().toString().replace("-", ""), encoder.encode(CURRENT), administrator);
    }
    private String username(long id) { return jdbc.queryForObject("select username from sys_user where id=?", String.class, id); }
    private String hash(long id) { return jdbc.queryForObject("select password_hash from sys_user where id=?", String.class, id); }
    private SystemUserDetails details(long id) {
        var principal = identities.loadPrincipal(username(id));
        return new SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
    }
    private Authentication authentication(long id) {
        var details = details(id);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }
    private void assertUnchanged(long id) {
        assertThat(encoder.matches(CURRENT, hash(id))).isTrue();
        assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id)).isZero();
    }
}
