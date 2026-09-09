package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.*;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionTransactionsIT extends IsolatedUserDatabase {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired FindByIndexNameSessionRepository repository;
    @Autowired SystemUserMapper users;
    @Autowired AuthIdentityService identities;
    @Autowired ObjectMapper json;
    @MockitoSpyBean UserSessionService sessions;

    @Test void rollbackPreservesAuthorityAuditVersionsAndSessionsThenCommitRevokes() throws Exception {
        var f = fixture(); Session stored = session(f.username()); var stale = details(f.username());
        try {
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                change(f, "DISABLED", 0, details(f.adminName()), 200);
                assertThat(repository.findById(stored.getId())).isNotNull();
                tx.setRollbackOnly();
            });
            assertThat(state(f)).isEqualTo("ENABLED"); assertThat(version(f.user())).isZero();
            assertThat(audits(f)).isZero(); assertThat(repository.findById(stored.getId())).isNotNull();
            assertFilter(stale, 200);
            change(f, "DISABLED", 0, details(f.adminName()), 200);
            assertThat(version(f.user())).isEqualTo(1); assertThat(repository.findById(stored.getId())).isNull();
            assertThat(users.selectEnabledPermissionCodes(f.user())).doesNotContain(f.code());
            assertThat(audits(f)).isEqualTo(1); assertFilter(stale, 401);
            assertThat(jdbc.queryForObject("select actor_user_id from sys_operation_log where target_type='PERMISSION' and target_id=?", Long.class, Long.toString(f.permission()))).isEqualTo(f.admin());
        } finally { repository.deleteById(stored.getId()); cleanup(f); }
    }

    @Test void retainedDisabledRoleGrantsInvalidateButRemovedAssociationsDoNotAndCleanupContinues() throws Exception {
        var f = fixture();
        long other = fixtureUser(false), removed = fixtureUser(false);
        long role = jdbc.queryForObject("insert into sys_role(code,name,status) values(?,'Disabled','DISABLED') returning id", Long.class, "role" + suffix());
        jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)", role, f.permission());
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", other, role);
        jdbc.update("insert into sys_user_role(user_id,role_id,deleted) values(?,?,true)", removed, role);
        Session failed = session(f.username()), successful = session(username(other));
        var stale = details(f.username());
        try {
            doThrow(new IllegalStateException("Simulated cleanup failure")).when(sessions).invalidateAll(f.username());
            change(f, "DISABLED", 0, details(f.adminName()), 200);
            assertThat(version(f.user())).isEqualTo(1); assertThat(version(other)).isEqualTo(1); assertThat(version(removed)).isZero();
            assertThat(repository.findById(failed.getId())).isNotNull(); assertThat(repository.findById(successful.getId())).isNull(); assertFilter(stale, 401);
            change(f, "ENABLED", 1, details(f.adminName()), 200);
            assertThat(version(f.user())).isEqualTo(2); assertThat(version(other)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from sys_role_permission where permission_id=? and deleted=false", Long.class, f.permission())).isEqualTo(2);
        } finally {
            reset(sessions); repository.deleteById(failed.getId()); repository.deleteById(successful.getId());
            jdbc.update("delete from sys_user_role where role_id=?", role); jdbc.update("delete from sys_role_permission where role_id=?", role); jdbc.update("delete from sys_role where id=?", role);
            jdbc.update("delete from sys_user where id in (?,?)", other, removed); cleanup(f);
        }
    }

    @Test void auditDatabaseFailureRollsBackMutationAndNeverInvalidatesSessions() {
        var f = fixture(); Session stored = session(f.username());
        String constraint = "permission_audit_" + suffix();
        try {
            jdbc.execute("alter table sys_operation_log add constraint " + constraint + " check (target_type <> 'PERMISSION' or target_id <> '" + f.permission() + "')");
            try {
                var result = mvc.perform(post(base(f) + "/status").with(user(details(f.adminName()))).with(csrf()).contentType("application/json")
                        .content("{\"status\":\"DISABLED\",\"version\":0}")).andExpect(status().isConflict()).andReturn();
                assertThat(result.getResolvedException()).isInstanceOf(org.springframework.dao.DataAccessException.class);
            } catch (Exception failure) { throw new AssertionError(failure); }
            assertThat(state(f)).isEqualTo("ENABLED"); assertThat(version(f.user())).isZero(); assertThat(audits(f)).isZero();
            assertThat(repository.findById(stored.getId())).isNotNull();
        } finally { jdbc.execute("alter table sys_operation_log drop constraint if exists " + constraint); repository.deleteById(stored.getId()); cleanup(f); }
    }

    @Test void activationRejectsStaleIdentityAdministratorAndPermissionsUnderSecurityLock() {
        var f = fixture();
        try {
            jdbc.update("update sys_permission set status='DISABLED' where id=?", f.permission());
            var admin = details(f.adminName());
            for (String mutation : List.of("administrator=false", "status='DISABLED'", "deleted=true", "locked_until=CURRENT_TIMESTAMP + interval '1 hour'", "auth_version=1")) {
                jdbc.update("update sys_user set " + mutation + " where id=?", f.admin());
                // AccountVersionFilter may reject unusable identities before the service does.
                int expected = mutation.equals("administrator=false") || mutation.startsWith("locked_until") ? 403 : 401;
                change(f, "ENABLED", 0, admin, expected);
                jdbc.update("update sys_user set administrator=true,status='ENABLED',deleted=false,locked_until=null,auth_version=0 where id=?", f.admin());
            }
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:permission:status' and deleted=false", f.role());
            var operator = details(f.username());
            change(f, "ENABLED", 0, operator, 403);
            jdbc.update("update sys_role_permission set deleted=true where role_id=? and permission_id in (select id from sys_permission where code='system:permission:status')", f.role());
            change(f, "ENABLED", 0, operator, 403);
            assertThat(version(f.user())).isZero(); assertThat(audits(f)).isZero();
            change(f, "ENABLED", 0, details(f.adminName()), 200);
            assertThat(version(f.user())).isEqualTo(1);
        } finally { cleanup(f); }
    }

    @Test void nonadministratorCannotDisableOwnCapabilityAndMetadataDoesNotInvalidate() throws Exception {
        var f = fixture();
        try {
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:permission:status' and deleted=false", f.role());
            change(f, "DISABLED", 0, details(f.username()), 403);
            mvc.perform(put(base(f)).with(user(details(f.adminName()))).with(csrf()).contentType("application/json")
                    .content("{\"permissionCode\":\"" + f.code() + "\",\"name\":\"Renamed\",\"type\":\"ACTION\",\"module\":\"catalog\",\"version\":0}"))
                    .andExpect(status().isOk());
            assertThat(version(f.user())).isZero();
            change(f, "ENABLED", 1, details(f.adminName()), 200); assertThat(version(f.user())).isZero();
        } finally { cleanup(f); }
    }

    @Test void softDeleteAndCodeReuseNeverReattachHistoricalGrantIds() throws Exception {
        var f = fixture();
        try {
            mvc.perform(delete(base(f)).param("version", "0").with(user(details(f.adminName()))).with(csrf())).andExpect(status().isOk());
            assertThat(version(f.user())).isEqualTo(1);
            String response = mvc.perform(post("/api/system/permissions").with(user(details(f.adminName()))).with(csrf()).contentType("application/json")
                    .content("{\"permissionCode\":\"" + f.code() + "\",\"name\":\"Replacement\",\"type\":\"ACTION\",\"module\":\"catalog\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            long replacement = json.readTree(response).path("data").path("id").asLong();
            assertThat(replacement).isNotEqualTo(f.permission());
            mvc.perform(post("/api/system/permissions/" + replacement + "/status").with(user(details(f.adminName()))).with(csrf()).contentType("application/json")
                    .content("{\"status\":\"ENABLED\",\"version\":0}")).andExpect(status().isOk());
            assertThat(users.selectEnabledPermissionCodes(f.user())).doesNotContain(f.code());
            assertThat(jdbc.queryForObject("select permission_id from sys_role_permission where role_id=?", Long.class, f.role())).isEqualTo(f.permission());
            jdbc.update("delete from sys_operation_log where target_type='PERMISSION' and target_id=?", Long.toString(replacement));
            jdbc.update("delete from sys_permission where id=?", replacement);
        } finally { cleanup(f); }
    }

    @Test void competingChangesReturnOneConflictAndSharedGrantWriterIsSerialized() throws Exception {
        var f = fixture(); ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        long newcomer = fixtureUser(false);
        try {
            var grant = pool.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                users.lockSecurityWrites();
                jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", newcomer, f.role());
                held.countDown(); await(release); return true;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            var actor = details(f.adminName());
            var disable = pool.submit(() -> { started.countDown(); change(f, "DISABLED", 0, actor, 200); });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> disable.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); grant.get(20, TimeUnit.SECONDS); disable.get(20, TimeUnit.SECONDS);
            assertThat(version(newcomer)).isEqualTo(1);
            new TransactionTemplate(manager).executeWithoutResult(tx -> { users.lockSecurityWrites(); assertThat(state(f)).isEqualTo("DISABLED"); });
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> enable = () -> {
                start.await(); return mvc.perform(post(base(f) + "/status").with(user(actor)).with(csrf()).contentType("application/json")
                        .content("{\"status\":\"ENABLED\",\"version\":1}")).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(enable); var second = pool.submit(enable); start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(version(newcomer)).isEqualTo(2);
        } finally { release.countDown(); pool.shutdownNow(); cleanup(f); jdbc.update("delete from sys_user where id=?", newcomer); }
    }

    @Test void delegatedActivationRejectsRetainedGrantOnDisabledUnassignedRole() {
        var f = fixture();
        try {
            jdbc.update("update sys_permission set status='DISABLED' where id=?", f.permission());
            jdbc.update("update sys_role set status='DISABLED' where id=?", f.role());
            jdbc.update("delete from sys_user_role where role_id=?", f.role());
            long operatorRole = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Operator') returning id", Long.class, "operator" + suffix());
            try {
                jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", f.user(), operatorRole);
                jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:permission:status' and deleted=false", operatorRole);
                change(f, "ENABLED", 0, details(f.username()), 403);
                assertThat(state(f)).isEqualTo("DISABLED"); assertThat(version(f.user())).isZero(); assertThat(audits(f)).isZero();
            } finally {
                jdbc.update("delete from sys_user_role where role_id=?", operatorRole);
                jdbc.update("delete from sys_role_permission where role_id=?", operatorRole);
                jdbc.update("delete from sys_role where id=?", operatorRole);
            }
        } finally { cleanup(f); }
    }

    @Test void preloadedActorIsRevalidatedAfterAdvisoryLockWaitAndConcurrentRevocation() throws Exception {
        var f = fixture(); ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1), loaded = new CountDownLatch(1), release = new CountDownLatch(1);
        Session stored = session(f.username());
        try {
            var holder = pool.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                users.lockSecurityWrites(); held.countDown(); await(loaded);
                jdbc.update("update sys_user set administrator=false where id=?", f.admin());
                await(release); return true;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            var waiter = pool.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                var actor = details(f.adminName());
                assertThat(users.selectById(f.admin()).getAdministrator()).isTrue();
                users.selectEnabledPermissionCodes(f.admin());
                loaded.countDown(); change(f, "DISABLED", 0, actor, 403); tx.setRollbackOnly(); return true;
            }));
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> waiter.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(20, TimeUnit.SECONDS); waiter.get(20, TimeUnit.SECONDS);
            assertThat(state(f)).isEqualTo("ENABLED"); assertThat(version(f.user())).isZero(); assertThat(audits(f)).isZero();
            assertThat(repository.findById(stored.getId())).isNotNull();
        } finally { release.countDown(); loaded.countDown(); pool.shutdownNow(); repository.deleteById(stored.getId()); cleanup(f); }
    }

    private void change(Fixture f, String state, int version, SystemUserDetails actor, int expected) {
        try { mvc.perform(post(base(f) + "/status").with(user(actor)).with(csrf()).contentType("application/json")
                .content("{\"status\":\"" + state + "\",\"version\":" + version + "}")).andExpect(status().is(expected)); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void assertFilter(SystemUserDetails details, int expected) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        try {
            var request = new MockHttpServletRequest(); var session = new MockHttpSession(); request.setSession(session);
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            new AccountVersionFilter(users, json).doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(expected);
            if (expected == 401) { assertThat(session.isInvalid()).isTrue(); assertThat(chain.getRequest()).isNull(); }
            else assertThat(chain.getRequest()).isNotNull();
        } finally { SecurityContextHolder.clearContext(); }
    }
    private Fixture fixture() {
        long admin = fixtureUser(true), user = fixtureUser(false);
        String code = "catalog:transaction:" + suffix();
        long permission = jdbc.queryForObject("insert into sys_permission(code,name,module,resource_type) values(?,'Fixture','catalog','ACTION') returning id", Long.class, code);
        long role = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Fixture') returning id", Long.class, "role" + suffix());
        jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)", role, permission);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", user, role);
        return new Fixture(admin, username(admin), user, username(user), role, permission, code);
    }
    private long fixtureUser(boolean admin) { return jdbc.queryForObject("insert into sys_user(username,display_name,status,administrator,must_change_password) values(?,'Fixture','ENABLED',?,false) returning id", Long.class, "permissiontx" + suffix(), admin); }
    private String username(long id) { return jdbc.queryForObject("select username from sys_user where id=?", String.class, id); }
    private SystemUserDetails details(String name) { var principal = identities.loadPrincipal(name); return new SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true); }
    private Session session(String name) { Session session = repository.createSession(); session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, name); repository.save(session); return session; }
    private long version(long id) { return jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id); }
    private String state(Fixture f) { return jdbc.queryForObject("select status from sys_permission where id=?", String.class, f.permission()); }
    private long audits(Fixture f) { return jdbc.queryForObject("select count(*) from sys_operation_log where target_type='PERMISSION' and target_id=?", Long.class, Long.toString(f.permission())); }
    private String base(Fixture f) { return "/api/system/permissions/" + f.permission(); }
    private String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private void await(CountDownLatch latch) { try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } }
    private void cleanup(Fixture f) {
        jdbc.update("delete from sys_operation_log where target_type='PERMISSION' and target_id=?", Long.toString(f.permission()));
        jdbc.update("delete from sys_role_permission where role_id=?", f.role()); jdbc.update("delete from sys_user_role where role_id=?", f.role());
        jdbc.update("delete from sys_role where id=?", f.role()); jdbc.update("delete from sys_permission where id=?", f.permission());
        jdbc.update("delete from sys_user where id in (?,?)", f.admin(), f.user());
    }
    private record Fixture(long admin, String adminName, long user, String username, long role, long permission, String code) {}
}
