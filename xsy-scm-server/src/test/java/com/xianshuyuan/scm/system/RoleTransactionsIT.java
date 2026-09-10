package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.*;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import com.xianshuyuan.scm.system.service.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
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
@ActiveProfiles("test")
class RoleTransactionsIT extends IsolatedUserDatabase {
    @Autowired RoleService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired FindByIndexNameSessionRepository repository;
    @Autowired SystemUserMapper users;
    @Autowired AuthIdentityService identities;
    @Autowired ObjectMapper json;
    @MockitoSpyBean UserSessionService sessions;
    private DatabaseSecurityActor.Actor administrator;

    @AfterEach void deleteAdministrator() {
        if (administrator != null) {
            new DatabaseSecurityActor(jdbc, identities).delete(administrator);
            administrator = null;
        }
    }

    @Test void rollbackRetainsRolesPermissionsVersionsAuditAndCurrentSessionsThenCommitRevokes() throws Exception {
        var f = fixture();
        Session stored = session(f.username());
        try {
            var actor = identity(f.username());
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                service.changeStatus(f.role(), new RoleStatusRequest("DISABLED", 0), admin());
                assertThat(repository.findById(stored.getId())).isNotNull();
                tx.setRollbackOnly();
            });
            assertThat(service.detail(f.role()).status()).isEqualTo("ENABLED");
            assertThat(version(f.user())).isZero();
            assertThat(users.selectEnabledRoleCodes(f.user())).contains(f.code());
            assertThat(users.selectEnabledPermissionCodes(f.user())).contains("system:role:list");
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='ROLE' and target_id=?", Long.class, Long.toString(f.role()))).isZero();
            assertThat(repository.findById(stored.getId())).isNotNull();
            assertFilter(actor, 200);
            service.changeStatus(f.role(), new RoleStatusRequest("DISABLED", 0), admin());
            assertThat(repository.findById(stored.getId())).isNull();
            assertThat(version(f.user())).isEqualTo(1);
            assertThat(users.selectEnabledRoleCodes(f.user())).isEmpty();
            assertThat(users.selectEnabledPermissionCodes(f.user())).isEmpty();
            assertFilter(actor, 401);
        } finally { repository.deleteById(stored.getId()); cleanup(f); }
    }

    @Test void runtimeSessionFailureKeepsSuccessfulCommitRejectsStaleIdentityAndContinuesOtherUsers() throws Exception {
        var f = fixture();
        long other = user("other" + suffix());
        String otherName = username(other);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", other, f.role());
        Session failed = session(f.username()), successful = session(otherName);
        var actor = identity(f.username());
        try {
            doThrow(new IllegalStateException("Simulated repository failure")).when(sessions).invalidateAll(f.username());
            service.delete(f.role(), 0, admin());
            assertThat(repository.findById(failed.getId())).isNotNull();
            assertThat(repository.findById(successful.getId())).isNull();
            assertThat(version(f.user())).isEqualTo(1);
            assertThat(version(other)).isEqualTo(1);
            assertThat(users.selectEnabledRoleCodes(f.user())).isEmpty();
            assertThat(users.selectEnabledPermissionCodes(f.user())).isEmpty();
            assertFilter(actor, 401);
            assertThat(jdbc.queryForObject("select deleted from sys_role where id=?", Boolean.class, f.role())).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from sys_role_permission where role_id=? and deleted=false", Long.class, f.role())).isEqualTo(1);
        } finally {
            reset(sessions);
            repository.deleteById(failed.getId()); repository.deleteById(successful.getId());
            cleanup(f); jdbc.update("delete from sys_user where id=?", other);
        }
    }

    @Test void enabledCodeChangesAndEnableRevokeButMetadataAndNoopStatusDoNot() {
        var f = fixture();
        try {
            service.update(f.role(), new UpdateRoleRequest(f.code(), "Renamed", "Description", 0), admin());
            assertThat(version(f.user())).isZero();
            service.changeStatus(f.role(), new RoleStatusRequest("ENABLED", 1), admin());
            assertThat(version(f.user())).isZero();
            service.update(f.role(), new UpdateRoleRequest(f.code() + "new", "Renamed", null, 2), admin());
            assertThat(version(f.user())).isEqualTo(1);
            service.changeStatus(f.role(), new RoleStatusRequest("DISABLED", 3), admin());
            jdbc.update("update sys_user set administrator=true where id=?", f.user());
            service.changeStatus(f.role(), new RoleStatusRequest("ENABLED", 4), identity(f.username()));
            assertThat(version(f.user())).isEqualTo(3);
            assertThat(users.selectEnabledRoleCodes(f.user())).containsExactly(f.code() + "new");
        } finally { cleanup(f); }
    }

    @Test void auditFailureRollsBackRoleAndUserVersionsWithoutInvalidating() {
        var f = fixture();
        Session stored = session(f.username());
        try {
            // Database constraint failure in transactional audit after mutation and callback registration.
            var invalidActor = new UsernamePasswordAuthenticationToken("x".repeat(101), "", List.of(() -> "system.administrator"));
            assertThatThrownBy(() -> service.changeStatus(f.role(), new RoleStatusRequest("DISABLED", 0), invalidActor))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(service.detail(f.role()).status()).isEqualTo("ENABLED");
            assertThat(version(f.user())).isZero();
            assertThat(repository.findById(stored.getId())).isNotNull();
        } finally { repository.deleteById(stored.getId()); cleanup(f); }
    }

    @Test void opposingStatusWritesSerializeAndRejectStaleVersion() throws Exception {
        var f = fixture();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> change = () -> {
                start.await();
                try { service.changeStatus(f.role(), new RoleStatusRequest("DISABLED", 0), admin()); return true; }
                catch (com.xianshuyuan.scm.common.exception.BusinessException conflict) {
                    assertThat(conflict.getErrorCode()).isEqualTo(com.xianshuyuan.scm.common.exception.ErrorCode.DATA_CONFLICT);
                    return false;
                }
            };
            var first = pool.submit(change); var second = pool.submit(change); start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(version(f.user())).isEqualTo(1);
        } finally { pool.shutdownNow(); cleanup(f); }
    }

    @Test void concurrentSelfRevocationCannotRemoveLastRemainingCapability() throws Exception {
        var f = fixture();
        long otherRole = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Other') returning id", Long.class, "other" + suffix());
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", f.user(), otherRole);
        for (long role : List.of(f.role(), otherRole))
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:status' and deleted=false", role);
        var actor = identity(f.username());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = List.of(f.role(), otherRole).stream().map(role -> pool.submit(() -> {
                start.await();
                try { service.changeStatus(role, new RoleStatusRequest("DISABLED", 0), actor); return true; }
                catch (com.xianshuyuan.scm.common.exception.BusinessException denied) {
                    assertThat(denied.getErrorCode()).isEqualTo(SystemErrorCodes.PROTECTED_ROLE); return false;
                }
            })).toList();
            start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(users.selectEnabledPermissionCodes(f.user())).contains("system:role:status");
        } finally {
            pool.shutdownNow(); cleanup(f);
            jdbc.update("delete from sys_role_permission where role_id=?", otherRole);
            jdbc.update("delete from sys_user_role where role_id=?", otherRole);
            jdbc.update("delete from sys_operation_log where target_type='ROLE' and target_id=?", Long.toString(otherRole));
            jdbc.update("delete from sys_role where id=?", otherRole);
        }
    }

    @Test void sharedLockSerializesGrantConsumerBeforeDisableAndConsumerRechecksAfterDisable() throws Exception {
        var f = fixture();
        long newcomer = user("new" + suffix());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch granted = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        try {
            var grant = pool.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                users.lockSecurityWrites();
                assertThat(service.detail(f.role()).status()).isEqualTo("ENABLED");
                jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", newcomer, f.role());
                granted.countDown(); await(release); return true;
            }));
            assertThat(granted.await(10, TimeUnit.SECONDS)).isTrue();
            var disable = pool.submit(() -> { started.countDown(); return service.changeStatus(f.role(), new RoleStatusRequest("DISABLED", 0), admin()); });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> disable.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); grant.get(20, TimeUnit.SECONDS); disable.get(20, TimeUnit.SECONDS);
            assertThat(version(newcomer)).isEqualTo(1);
            assertThat(users.selectEnabledRoleCodes(newcomer)).isEmpty();
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                users.lockSecurityWrites();
                assertThat(service.detail(f.role()).status()).isEqualTo("DISABLED");
                // Future assignment commands must reject here, not consume an earlier enabled read.
            });
        } finally { release.countDown(); pool.shutdownNow(); cleanup(f); jdbc.update("delete from sys_user where id=?", newcomer); }
    }

    @Test void roleNamedAdminNeverElevatesAndLastUsableFlagAdminSurvivesRoleDeletion() {
        var f = fixture();
        long usableAdministratorsBefore = users.countUsableAdministratorsExcept(-1);
        try {
            jdbc.update("update sys_role set code='administrator' where id=?", f.role());
            assertThat(AuthorityRules.isAdministrator(identity(f.username()))).isFalse();
            jdbc.update("update sys_role set code=? where id=?", f.code(), f.role());
            jdbc.update("update sys_user set administrator=true,password_hash=? where id=?", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("test-password-123"), f.user());
            service.delete(f.role(), 0, identity(f.username()));
            assertThat(AuthorityRules.isAdministrator(identity(f.username()))).isTrue();
            assertThat(users.countUsableAdministratorsExcept(-1)).isEqualTo(usableAdministratorsBefore + 1);
        } finally { cleanup(f); }
    }

    @Test void statusOnlyActorCannotActivateStrongerRoleForSelfOrOthers() {
        for (boolean self : List.of(true, false)) {
            var operator = fixture(); var target = fixture();
            try {
                jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:status' and deleted=false", operator.role());
                jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:user:delete' and deleted=false", target.role());
                jdbc.update("update sys_role set status='DISABLED' where id=?", target.role());
                if (self) jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", operator.user(), target.role());
                assertActivationDenied(target, identity(operator.username()));
                assertThat(version(operator.user())).isZero();
            } finally { cleanup(target); cleanup(operator); }
        }
    }

    @Test void activationUsesDatabasePermissionsAndCurrentAdministratorFlagNotStaleAuthorities() {
        var operator = fixture(); var target = fixture();
        try {
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code in ('system:role:status','system:user:delete') and deleted=false", operator.role());
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:user:delete' and deleted=false", target.role());
            jdbc.update("update sys_role set status='DISABLED' where id=?", target.role());
            var stalePermissions = identity(operator.username());
            jdbc.update("update sys_role_permission set deleted=true where role_id=? and permission_id in (select id from sys_permission where code='system:user:delete')", operator.role());
            assertActivationDenied(target, stalePermissions);
            jdbc.update("update sys_user set administrator=true where id=?", operator.user());
            var staleAdmin = identity(operator.username());
            jdbc.update("update sys_user set administrator=false where id=?", operator.user());
            assertActivationDenied(target, staleAdmin);
            jdbc.update("update sys_role_permission set deleted=false where role_id=?", operator.role());
            jdbc.update("update sys_role set system_role=true where id=?", target.role());
            assertActivationDenied(target, staleAdmin);
            jdbc.update("update sys_role set system_role=false where id=?", target.role());
            assertActivationDenied(target, new UsernamePasswordAuthenticationToken(
                    "operator", "", List.of(() -> "system.administrator")));
            jdbc.update("update sys_user set administrator=true where id=?", operator.user());
            assertThat(service.changeStatus(target.role(), new RoleStatusRequest("ENABLED", 0), identity(operator.username())).status()).isEqualTo("ENABLED");
        } finally { cleanup(target); cleanup(operator); }
    }

    @Test void activationAcceptsPermissionSubsetButRejectsInactiveOrStaleIdentity() {
        var operator = fixture(); var target = fixture();
        try {
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:status' and deleted=false", operator.role());
            jdbc.update("update sys_role set status='DISABLED' where id=?", target.role());
            var actor = identity(operator.username());
            for (String mutation : List.of("status='DISABLED'", "deleted=true", "locked_until=CURRENT_TIMESTAMP + interval '1 hour'", "auth_version=1")) {
                jdbc.update("update sys_user set " + mutation + " where id=?", operator.user());
                assertActivationDenied(target, actor);
                jdbc.update("update sys_user set status='ENABLED',deleted=false,locked_until=null,auth_version=0 where id=?", operator.user());
            }
            assertThat(service.changeStatus(target.role(), new RoleStatusRequest("ENABLED", 0), actor).status()).isEqualTo("ENABLED");
        } finally { cleanup(target); cleanup(operator); }
    }

    private void assertActivationDenied(Fixture target, Authentication actor) {
        assertThatThrownBy(() -> service.changeStatus(target.role(), new RoleStatusRequest("ENABLED", 0), actor))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class)
                .extracting("errorCode").isEqualTo(SystemErrorCodes.PROTECTED_ROLE);
        assertThat(service.detail(target.role()).status()).isEqualTo("DISABLED");
        assertThat(service.detail(target.role()).version()).isZero();
        assertThat(version(target.user())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='ROLE' and target_id=?", Long.class, Long.toString(target.role()))).isZero();
    }

    private void assertFilter(UsernamePasswordAuthenticationToken actor, int expected) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(actor);
        try {
            var request = new MockHttpServletRequest();
            var session = new MockHttpSession(); request.setSession(session);
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            new AccountVersionFilter(users, json).doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(expected);
            if (expected == 401) { assertThat(session.isInvalid()).isTrue(); assertThat(chain.getRequest()).isNull(); }
            else assertThat(chain.getRequest()).isNotNull();
        } finally { SecurityContextHolder.clearContext(); }
    }
    private UsernamePasswordAuthenticationToken identity(String username) {
        var principal = identities.loadPrincipal(username); var authorities = identities.loadAuthorities(principal);
        return new UsernamePasswordAuthenticationToken(new SystemUserDetails(principal, null, authorities, true, true), null, authorities);
    }
    private Session session(String username) {
        Session session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username); repository.save(session); return session;
    }
    private Fixture fixture() {
        String name = "roleuser" + suffix(), code = "role" + suffix();
        long user = user(name);
        long role = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Role') returning id", Long.class, code);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", user, role);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:list' and deleted=false", role);
        return new Fixture(user, role, name, code);
    }
    private long user(String name) { return jdbc.queryForObject("insert into sys_user(username,display_name,status,must_change_password) values(?,'Fixture','ENABLED',false) returning id", Long.class, name); }
    private String username(long id) { return jdbc.queryForObject("select username from sys_user where id=?", String.class, id); }
    private long version(long id) { return jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id); }
    private Authentication admin() {
        if (administrator == null)
            administrator = new DatabaseSecurityActor(jdbc, identities).createAdministrator("roletxadmin");
        return administrator.authentication();
    }
    private void await(CountDownLatch latch) { try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } }
    private String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private void cleanup(Fixture f) {
        jdbc.update("delete from sys_operation_log where target_type='ROLE' and target_id=?", Long.toString(f.role()));
        jdbc.update("delete from sys_role_permission where role_id=?", f.role());
        jdbc.update("delete from sys_user_role where role_id=?", f.role());
        jdbc.update("delete from sys_role where id=?", f.role());
        jdbc.update("delete from sys_user where id=?", f.user());
    }
    private record Fixture(long user, long role, String username, String code) {}
}
