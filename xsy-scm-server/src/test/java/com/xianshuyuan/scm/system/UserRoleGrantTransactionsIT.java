package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.system.dto.ReplaceUserRolesRequest;
import com.xianshuyuan.scm.system.service.UserRoleGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRoleGrantTransactionsIT extends IsolatedUserDatabase {
    @Autowired UserRoleGrantService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthIdentityService identities;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.xianshuyuan.scm.system.service.RoleService roleService;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.springframework.session.FindByIndexNameSessionRepository repository;
    @MockitoSpyBean UserSessionService sessions;

    @Test void rollbackRetainsRelationsVersionsAuditAndDoesNotInvalidateThenCommitDoes() {
        long admin = account(true), target = account(false), role = role();
        var actor = identity(admin);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), actor);
            verify(sessions, never()).invalidateAll(username(target));
            tx.setRollbackOnly();
        });
        assertThat(service.read(target).roles()).isEmpty();
        assertThat(service.read(target).version()).isZero();
        assertThat(authVersion(target)).isZero();
        assertThat(audits(target)).isZero();
        service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), actor);
        verify(sessions).invalidateAll(username(target));
        assertThat(authVersion(target)).isEqualTo(1);
        assertThat(audits(target)).isEqualTo(1);
        service.replace(target, new ReplaceUserRolesRequest(List.of(role), 1), actor);
        verify(sessions, times(1)).invalidateAll(username(target));
        assertThat(audits(target)).isEqualTo(1);
    }

    @Test void disabledTargetCanReceiveAndRevokeRolesWithoutChangingAccountState() {
        long admin = account(true), target = account(false), role = role();
        jdbc.update("update sys_user set status='DISABLED',locked_until=timestamp with time zone '2099-01-01 00:00:00+00' where id=?", target);
        var before = jdbc.queryForMap("select status,locked_until,administrator from sys_user where id=?", target);
        var actor = identity(admin);
        assertThatCode(() -> service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), actor)).doesNotThrowAnyException();
        service.replace(target, new ReplaceUserRolesRequest(List.of(), 1), actor);
        assertThat(service.read(target).roles()).isEmpty();
        assertThat(service.read(target).version()).isEqualTo(2);
        assertThat(authVersion(target)).isEqualTo(2);
        assertThat(audits(target)).isEqualTo(2);
        assertThat(jdbc.queryForMap("select status,locked_until,administrator from sys_user where id=?", target)).isEqualTo(before);
        verify(sessions, times(2)).invalidateAll(username(target));
    }

    @Test void runtimeSessionFailureCannotUndoCommittedGrant() {
        long admin = account(true), target = account(false), role = role();
        doThrow(new IllegalStateException("Simulated session failure")).when(sessions).invalidateAll(username(target));
        service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), identity(admin));
        assertThat(service.read(target).roles()).hasSize(1);
        assertThat(authVersion(target)).isEqualTo(1);
        assertThat(audits(target)).isEqualTo(1);
    }

    @Test void auditFailureRollsBackRelationAndVersions() {
        long admin = account(true), target = account(false), role = role();
        jdbc.execute("alter table sys_operation_log add constraint reject_user_grant_test check(operation_code <> 'USER_ASSIGN_ROLES') not valid");
        try {
            assertThatThrownBy(() -> service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), identity(admin))).isInstanceOf(RuntimeException.class);
            assertThat(service.read(target).roles()).isEmpty();
            assertThat(service.read(target).version()).isZero();
            assertThat(authVersion(target)).isZero();
            verify(sessions, never()).invalidateAll(username(target));
        } finally { jdbc.execute("alter table sys_operation_log drop constraint reject_user_grant_test"); }
    }

    @Test void competingReplacementsHaveExactlyOneWinner() throws Exception {
        long admin = account(true), target = account(false), first = role(), second = role();
        var actor = identity(admin);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> replaceConcurrently(target, first, actor, ready, start));
            var b = pool.submit(() -> replaceConcurrently(target, second, actor, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(service.read(target).roles()).hasSize(1);
            assertThat(service.read(target).version()).isEqualTo(1);
            assertThat(authVersion(target)).isEqualTo(1);
            assertThat(audits(target)).isEqualTo(1);
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void grantWaitsForSecurityWriterThenRejectsStaleActor() throws Exception {
        long admin = account(true), target = account(false), role = role();
        var actor = identity(admin);
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var writer = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                jdbc.execute("select pg_advisory_xact_lock(20260908,1)");
                jdbc.update("update sys_user set auth_version=auth_version+1 where id=?", admin);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var grant = pool.submit(() -> service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), actor));
            assertThatThrownBy(() -> grant.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); writer.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> grant.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
            assertThat(service.read(target).roles()).isEmpty();
            assertThat(authVersion(target)).isZero();
            assertThat(audits(target)).isZero();
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void survivingJdbcSessionIsRejectedAfterCleanupFailure() throws Exception {
        long admin = account(true), target = account(true), role = role();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(identity(target));
        org.springframework.session.Session stored = repository.createSession();
        stored.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        stored.setAttribute(org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username(target));
        repository.save(stored);
        var cookie = new jakarta.servlet.http.Cookie("XSY_SESSION", java.util.Base64.getEncoder().encodeToString(stored.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system/users").cookie(cookie))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            doThrow(new IllegalStateException("Simulated cleanup failure")).when(sessions).invalidateAll(username(target));
            service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), identity(admin));
            assertThat(repository.findById(stored.getId())).isNotNull();
            assertThat(service.read(target).version()).isEqualTo(1);
            assertThat(authVersion(target)).isEqualTo(1);
            assertThat(audits(target)).isEqualTo(1);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system/users").cookie(cookie))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        } finally { repository.deleteById(stored.getId()); }
    }

    @Test void roleDisableAndGrantSerializeInBothOrdersWithConfirmedAdvisoryWaiter() throws Exception {
        for (boolean grantFirst : List.of(false, true)) {
            long admin = account(true), target = account(false), role = role();
            var actor = identity(admin);
            var pool = Executors.newFixedThreadPool(2);
            var held = new CountDownLatch(1); var release = new CountDownLatch(1);
            var waiterPid = new java.util.concurrent.atomic.AtomicInteger();
            try {
                var first = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                    if (grantFirst) service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), actor);
                    else roleService.changeStatus(role, new com.xianshuyuan.scm.system.dto.RoleStatusRequest("DISABLED", 0), actor);
                    held.countDown();
                    awaitRelease(release);
                }));
                assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                    waiterPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    if (grantFirst) roleService.changeStatus(role, new com.xianshuyuan.scm.system.dto.RoleStatusRequest("DISABLED", 0), actor);
                    else service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), actor);
                }));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_locks where pid=? and locktype='advisory' and classid=20260908 and objid=1 and not granted)", Boolean.class, waiterPid.get()));
                    if (waiting) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("actual service advisory waiter, grantFirst=%s", grantFirst).isTrue();
                release.countDown(); first.get(10, TimeUnit.SECONDS);
                if (grantFirst) second.get(10, TimeUnit.SECONDS);
                else assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
                assertThat(roleService.detail(role).status()).isEqualTo("DISABLED");
                assertThat(service.read(target).roles()).hasSize(grantFirst ? 1 : 0);
                assertThat(service.read(target).version()).isEqualTo(grantFirst ? 1 : 0);
                assertThat(authVersion(target)).isEqualTo(grantFirst ? 2 : 0);
                assertThat(audits(target)).isEqualTo(grantFirst ? 1 : 0);
                assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='ROLE' and target_id=?", Long.class, Long.toString(role))).isEqualTo(1);
            } finally { release.countDown(); pool.shutdownNow(); }
        }
    }

    private void awaitRelease(CountDownLatch release) {
        try { if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }

    private boolean replaceConcurrently(long target, long role, Authentication actor, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
        try { service.replace(target, new ReplaceUserRolesRequest(List.of(role), 0), actor); return true; }
        catch (com.xianshuyuan.scm.common.exception.BusinessException conflict) { return false; }
    }
    private long account(boolean admin) { return jdbc.queryForObject("insert into sys_user(username,display_name,administrator,must_change_password) values(?,'Transaction grant',?,false) returning id", Long.class, "granttx" + UUID.randomUUID().toString().replace("-", ""), admin); }
    private long role() { return jdbc.queryForObject("insert into sys_role(code,name) values(?,'Transaction role') returning id", Long.class, "granttx" + UUID.randomUUID().toString().replace("-", "")); }
    private String username(long id) { return jdbc.queryForObject("select username from sys_user where id=?", String.class, id); }
    private long authVersion(long id) { return jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id); }
    private long audits(long id) { return jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(id)); }
    private Authentication identity(long id) {
        var principal = identities.loadPrincipal(username(id));
        var details = new SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }
}
