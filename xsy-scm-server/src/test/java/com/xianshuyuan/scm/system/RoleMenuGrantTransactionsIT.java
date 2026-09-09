package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.system.dto.ReplaceRoleMenusRequest;
import com.xianshuyuan.scm.system.service.RoleMenuGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleMenuGrantTransactionsIT extends IsolatedUserDatabase {
    @Autowired RoleMenuGrantService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthIdentityService identities;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.springframework.session.FindByIndexNameSessionRepository repository;
    @MockitoSpyBean UserSessionService sessions;

    @Test void rollbackThenCommitInvalidatesEveryRetainedUserEvenOnDisabledRoleAndNoopDoesNothing() {
        long admin=account(true), first=account(false), second=account(false), role=role(), menu=menu();
        associate(first,role); associate(second,role);
        jdbc.update("update sys_role set status='DISABLED' where id=?",role);
        var actor=identity(admin);
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),0),actor);
            verify(sessions,never()).invalidateAll(username(first)); tx.setRollbackOnly();
        });
        assertThat(service.read(role).menus()).isEmpty(); assertThat(service.read(role).version()).isZero();
        assertThat(authVersion(first)).isZero(); assertThat(authVersion(second)).isZero(); assertThat(audits(role)).isZero();
        doThrow(new IllegalStateException("Simulated cleanup failure")).when(sessions).invalidateAll(username(first));
        service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),0),actor);
        verify(sessions).invalidateAll(username(first)); verify(sessions).invalidateAll(username(second));
        assertThat(authVersion(first)).isEqualTo(1); assertThat(authVersion(second)).isEqualTo(1);
        assertThat(service.read(role).version()).isEqualTo(1); assertThat(audits(role)).isEqualTo(1);
        service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),1),actor);
        verify(sessions,times(1)).invalidateAll(username(second)); assertThat(authVersion(second)).isEqualTo(1);
        verify(sessions,never()).invalidateAll(username(admin));
    }

    @Test void auditFailureRollsBackRelationsRoleVersionAndUserVersions() {
        long admin=account(true), target=account(false), role=role(), menu=menu(); associate(target,role);
        jdbc.execute("alter table sys_operation_log add constraint reject_role_menu_test check(operation_code <> 'ROLE_ASSIGN_MENUS') not valid");
        try {
            assertThatThrownBy(()->service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),0),identity(admin))).isInstanceOf(RuntimeException.class);
            assertThat(service.read(role).menus()).isEmpty(); assertThat(service.read(role).version()).isZero();
            assertThat(authVersion(target)).isZero(); assertThat(audits(role)).isZero();
            verify(sessions,never()).invalidateAll(username(target));
        } finally { jdbc.execute("alter table sys_operation_log drop constraint reject_role_menu_test"); }
    }

    @Test void jdbcSessionSurvivesCleanupFailureButAccountVersionFilterRejectsSameCookie() throws Exception {
        long admin=account(true), target=account(true), role=role(), menu=menu(); associate(target,role);
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext(); context.setAuthentication(identity(target));
        org.springframework.session.Session stored=repository.createSession();
        stored.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        stored.setAttribute(org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,username(target)); repository.save(stored);
        var cookie=new jakarta.servlet.http.Cookie("XSY_SESSION",Base64.getEncoder().encodeToString(stored.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system/roles").cookie(cookie)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            doThrow(new IllegalStateException("Simulated cleanup failure")).when(sessions).invalidateAll(username(target));
            service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),0),identity(admin));
            verify(sessions).invalidateAll(username(target));
            assertThat(repository.findById(stored.getId())).isNotNull(); assertThat(authVersion(target)).isEqualTo(1); assertThat(audits(role)).isEqualTo(1);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system/roles").cookie(cookie)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        } finally { repository.deleteById(stored.getId()); }
    }

    @Autowired com.xianshuyuan.scm.system.service.MenuService menuService;

    @Test void menuDisableAndGrantSerializeBothOrdersWithBackendSpecificAdvisoryWaiter() throws Exception {
        for(boolean grantFirst:List.of(false,true)) {
            long admin=account(true), target=account(false), role=role(), menu=menu(); associate(target,role); var actor=identity(admin);
            var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            var held=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
            var pid=new java.util.concurrent.atomic.AtomicInteger();
            try {
                var first=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(tx->{
                    if(grantFirst) service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),0),actor);
                    else menuService.changeStatus(menu,new com.xianshuyuan.scm.system.dto.MenuStatusRequest("DISABLED",0),actor);
                    held.countDown(); await(release);
                }));
                assertThat(held.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(tx->{
                    pid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                    if(grantFirst) menuService.changeStatus(menu,new com.xianshuyuan.scm.system.dto.MenuStatusRequest("DISABLED",0),actor);
                    else service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),0),actor);
                }));
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10); boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_locks where pid=? and locktype='advisory' and classid=20260908 and objid=1 and not granted)",Boolean.class,pid.get()));
                    if(waiting) break; Thread.sleep(20);
                }
                assertThat(waiting).as("real service advisory waiter, grantFirst=%s",grantFirst).isTrue();
                release.countDown(); first.get(10,java.util.concurrent.TimeUnit.SECONDS);
                if(grantFirst) second.get(10,java.util.concurrent.TimeUnit.SECONDS);
                else assertThatThrownBy(()->second.get(10,java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
                assertThat(service.read(role).menus()).hasSize(grantFirst?1:0);
                assertThat(service.read(role).version()).isEqualTo(grantFirst?1:0); assertThat(authVersion(target)).isEqualTo(grantFirst?2:0);
                assertThat(audits(role)).isEqualTo(grantFirst?1:0);
                assertThat(jdbc.queryForObject("select status from sys_menu where id=?",String.class,menu)).isEqualTo("DISABLED");
            } finally { release.countDown(); pool.shutdownNow(); }
        }
    }

    @Test void competingSameVersionReplacementsHaveOneWinner() throws Exception {
        long admin=account(true), target=account(false), role=role(), a=menu(), b=menu(); associate(target,role);
        var actor=identity(admin); var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2); var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var futures=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(long menu:List.of(a,b)) futures.add(pool.submit(()->{
                ready.countDown(); await(start);
                try { service.replace(role,new ReplaceRoleMenusRequest(List.of(menu),0),actor); return true; }
                catch(com.xianshuyuan.scm.common.exception.BusinessException failure) {
                    assertThat(failure.getErrorCode()).isEqualTo(com.xianshuyuan.scm.common.exception.ErrorCode.DATA_CONFLICT); return false;
                }
            }));
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(futures.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS),futures.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(service.read(role).menus()).hasSize(1); assertThat(service.read(role).version()).isEqualTo(1);
            assertThat(authVersion(target)).isEqualTo(1); assertThat(audits(role)).isEqualTo(1);
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    private void await(java.util.concurrent.CountDownLatch release) { try { if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); } catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); } }
    private long account(boolean admin){return jdbc.queryForObject("insert into sys_user(username,display_name,administrator,must_change_password) values(?,'Role menu transaction',?,false) returning id",Long.class,"rmtx"+UUID.randomUUID().toString().replace("-",""),admin);}
    private long role(){return jdbc.queryForObject("insert into sys_role(code,name) values(?,'Role menu transaction') returning id",Long.class,"rmtx"+UUID.randomUUID().toString().replace("-",""));}
    private long menu(){return jdbc.queryForObject("insert into sys_menu(type,name) values('DIRECTORY','Menu transaction') returning id",Long.class);}
    private void associate(long user,long role){jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",user,role);}
    private String username(long id){return jdbc.queryForObject("select username from sys_user where id=?",String.class,id);}
    private long authVersion(long id){return jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,id);}
    private long audits(long id){return jdbc.queryForObject("select count(*) from sys_operation_log where target_type='ROLE' and operation_code='ROLE_ASSIGN_MENUS' and target_id=?",Long.class,Long.toString(id));}
    private Authentication identity(long id){var p=identities.loadPrincipal(username(id)); var details=new SystemUserDetails(p,null,identities.loadAuthorities(p),true,true);return UsernamePasswordAuthenticationToken.authenticated(details,null,details.getAuthorities());}
}
