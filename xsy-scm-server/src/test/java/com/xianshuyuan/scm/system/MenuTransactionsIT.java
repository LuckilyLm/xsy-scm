package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class MenuTransactionsIT extends IsolatedUserDatabase {
    @Autowired JdbcTemplate jdbc;
    @Autowired MenuService menus;
    @Autowired com.xianshuyuan.scm.system.service.PermissionService permissions;
    @Autowired com.xianshuyuan.scm.system.mapper.SystemUserMapper userMapper;
    @Autowired AuthIdentityService identities;
    @Autowired PlatformTransactionManager manager;
    @Autowired FindByIndexNameSessionRepository repository;
    @MockitoSpyBean UserSessionService sessions;

    @Test void parentMutationInvalidatesRetainedDescendantUsersOnlyAfterCommitAndSurvivesCleanupFailure() {
        var f = fixture(); Session first = session(f.first()), second = session(f.second());
        try {
            var tx = new TransactionTemplate(manager);
            tx.executeWithoutResult(status -> {
                menus.changeStatus(f.root(), new MenuStatusRequest("DISABLED", 0), actor(f.admin()));
                assertThat(version(f.first())).isEqualTo(1);
                assertThat(repository.findById(first.getId())).isNotNull();
                status.setRollbackOnly();
            });
            assertThat(version(f.first())).isZero(); assertThat(audits(f.root())).isZero();
            doThrow(new IllegalStateException("Simulated cleanup failure")).when(sessions).invalidateAll(f.first());
            menus.changeStatus(f.root(), new MenuStatusRequest("DISABLED", 0), actor(f.admin()));
            assertThat(version(f.first())).isEqualTo(1); assertThat(version(f.second())).isEqualTo(1);
            assertThat(repository.findById(first.getId())).isNotNull(); assertThat(repository.findById(second.getId())).isNull();
            assertThat(audits(f.root())).isEqualTo(1);
            assertThat(identities.loadAuthorities(identities.loadPrincipal(f.first()))).isEmpty();
        } finally { reset(sessions); repository.deleteById(first.getId()); repository.deleteById(second.getId()); }
    }

    @Test void auditFailureRollsBackMenuAndVersionsWithoutSessionCleanup() {
        var f = fixture(); Session stored = session(f.first()); String constraint = "menu_audit_" + suffix();
        try {
            jdbc.execute("alter table sys_operation_log add constraint " + constraint + " check(target_type <> 'MENU' or target_id <> '" + f.root() + "')");
            assertThatThrownBy(() -> menus.changeStatus(f.root(), new MenuStatusRequest("DISABLED", 0), actor(f.admin())))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(menus.detail(f.root()).status()).isEqualTo("ENABLED");
            assertThat(version(f.first())).isZero(); assertThat(audits(f.root())).isZero();
            assertThat(repository.findById(stored.getId())).isNotNull();
        } finally { jdbc.execute("alter table sys_operation_log drop constraint " + constraint); repository.deleteById(stored.getId()); }
    }

    @Test void retainedRoleNavigationCannotBeExpandedByAnOperatorWithoutTargetPermission() {
        var f = fixture();
        jdbc.update("insert into sys_permission(code,name,module,resource_type) values('menu_fixture:product:read','Product fixture','catalog','API'),('menu_fixture:customer:read','Customer fixture','catalog','API')");
        long operator = user("operator" + suffix(), false);
        String name = jdbc.queryForObject("select username from sys_user where id=?", String.class, operator);
        long role = role();
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", operator, role);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code in ('system:menu:update','system:menu:status') and deleted=false", role);
        assertThatCode(() -> menus.update(f.root(), new UpdateMenuRequest("DIRECTORY", null, "Restricted", null, null, null, "menu_fixture:product:read", 0, true, "ENABLED", 0), actor(name)))
                .doesNotThrowAnyException();
        assertThat(menus.detail(f.root()).requiredPermission()).isEqualTo("menu_fixture:product:read");
        assertThatThrownBy(() -> menus.update(f.root(), new UpdateMenuRequest("DIRECTORY", null, "Removed", null, null, null, null, 0, true, "ENABLED", 1), actor(name)))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
        assertThatThrownBy(() -> menus.update(f.root(), new UpdateMenuRequest("DIRECTORY", null, "Replaced", null, null, null, "menu_fixture:customer:read", 0, true, "ENABLED", 1), actor(name)))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
        jdbc.update("update sys_menu set status='DISABLED',version=0,required_permission_code='menu_fixture:product:read' where id=?", f.root());
        assertThatThrownBy(() -> menus.changeStatus(f.root(), new MenuStatusRequest("ENABLED", 0), actor(name)))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
        assertThatThrownBy(() -> menus.update(f.root(), new UpdateMenuRequest("DIRECTORY", null, "Expanded", null, null, null, null, 0, true, "ENABLED", 0), actor(name)))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
        assertThat(menus.detail(f.root()).version()).isZero();
    }

    @Test void permissionDeleteAndReferenceCreationSerializeInBothOrders() throws Exception {
        for (boolean referenceFirst : java.util.List.of(true, false)) {
            var f = fixture(); String code = "catalog:race" + suffix();
            long permission = jdbc.queryForObject("insert into sys_permission(code,name,module,resource_type) values(?,'Race','catalog','API') returning id", Long.class, code);
            var locked = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var first = pool.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                    userMapper.lockSecurityWrites();
                    if (referenceFirst) createReference(code, f.admin()); else permissions.delete(permission, 0, actor(f.admin()));
                    locked.countDown();
                    try { assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                    return true;
                }));
                assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> {
                    try {
                        if (referenceFirst) permissions.delete(permission, 0, actor(f.admin())); else createReference(code, f.admin());
                        return true;
                    } catch (com.xianshuyuan.scm.common.exception.BusinessException expected) { return false; }
                });
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_locks where locktype='advisory' and classid=20260908 and objid=1 and granted=false)", Boolean.class));
                    if (waiting) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("second transaction waits on shared security advisory lock").isTrue();
                assertThatThrownBy(() -> second.get(250, java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown();
                assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
                assertThat(jdbc.queryForObject("select count(*) from sys_menu m where m.deleted=false and m.required_permission_code=? and not exists(select 1 from sys_permission p where p.code=m.required_permission_code and p.deleted=false)", Long.class, code)).isZero();
            } finally { release.countDown(); pool.shutdownNow(); }
        }
    }
    void createReference(String code, String admin) {
        menus.create(new CreateMenuRequest("DIRECTORY", null, "Reference", null, null, null, code, 0, true, "ENABLED"), actor(admin));
    }

    @Test void opposingMovesSerializeAndCannotCommitACycle() throws Exception {
        var f = fixture();
        long other = jdbc.queryForObject("insert into sys_menu(type,name) values('DIRECTORY','Other') returning id", Long.class);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> move(start, f.root(), other, f.admin()));
            var two = pool.submit(() -> move(start, other, f.root(), f.admin()));
            start.countDown();
            assertThat(java.util.List.of(one.get(10, java.util.concurrent.TimeUnit.SECONDS), two.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(menus.detail(f.root()).parentId() == null || menus.detail(other).parentId() == null).isTrue();
        } finally { pool.shutdownNow(); }
    }
    boolean move(java.util.concurrent.CountDownLatch start, long id, long parent, String admin) throws Exception {
        start.await();
        try {
            menus.update(id, new UpdateMenuRequest("DIRECTORY", parent, "Moved", null, null, null, null, 0, true, "ENABLED", 0), actor(admin));
            return true;
        } catch (com.xianshuyuan.scm.common.exception.BusinessException expected) { return false; }
    }

    Fixture fixture() {
        String admin = "menuadmin" + suffix(), first = "member" + suffix(), second = "member" + suffix();
        user(admin, true); long one = user(first, false), two = user(second, false);
        long root = jdbc.queryForObject("insert into sys_menu(type,name) values('DIRECTORY','Root') returning id", Long.class);
        long child = jdbc.queryForObject("insert into sys_menu(parent_id,type,name) values(?,'DIRECTORY','Child') returning id", Long.class, root);
        long role = role(); jdbc.update("update sys_role set status='DISABLED' where id=?", role);
        jdbc.update("insert into sys_role_menu(role_id,menu_id) values(?,?)", role, child);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?),(?,?)", one, role, two, role);
        return new Fixture(admin, first, second, root);
    }
    long role() { return jdbc.queryForObject("insert into sys_role(code,name) values(?,'Role') returning id", Long.class, "role" + suffix()); }
    long user(String name, boolean admin) {
        return jdbc.queryForObject("insert into sys_user(username,display_name,administrator,must_change_password) values(?,'Fixture',?,false) returning id", Long.class, name, admin);
    }
    UsernamePasswordAuthenticationToken actor(String name) {
        var p = identities.loadPrincipal(name); var d = new SystemUserDetails(p, null, identities.loadAuthorities(p), true, true);
        return UsernamePasswordAuthenticationToken.authenticated(d, null, d.getAuthorities());
    }
    long version(String name) { return jdbc.queryForObject("select auth_version from sys_user where username=?", Long.class, name); }
    long audits(long id) { return jdbc.queryForObject("select count(*) from sys_operation_log where target_type='MENU' and target_id=?", Long.class, Long.toString(id)); }
    Session session(String name) {
        Session stored = repository.createSession(); stored.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, name);
        repository.save(stored); return stored;
    }
    static String suffix() { return UUID.randomUUID().toString().replace("-", ""); }
    record Fixture(String admin, String first, String second, long root) {}
}
