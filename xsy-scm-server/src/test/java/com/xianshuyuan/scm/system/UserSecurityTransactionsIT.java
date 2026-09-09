package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.system.dto.UserStatusRequest;
import com.xianshuyuan.scm.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserSecurityTransactionsIT extends IsolatedUserDatabase {
    @Autowired UserService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired FindByIndexNameSessionRepository repository;

    @Test void invalidatesPrincipalSessionsOnlyAfterCommitAndNotAfterRollback() {
        String name="session"+suffix(); long id=fixture(name,false);
        Session session=repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,name);
        repository.save(session);
        String sessionId=session.getId();
        try {
            var tx=new TransactionTemplate(manager);
            tx.executeWithoutResult(status -> {
                service.changeStatus(id,new UserStatusRequest("DISABLED",0),actor());
                assertThat(repository.findById(sessionId)).isNotNull();
                status.setRollbackOnly();
            });
            assertThat(repository.findById(sessionId)).isNotNull();
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?",Long.class,Long.toString(id))).isZero();
            tx.executeWithoutResult(status -> {
                service.changeStatus(id,new UserStatusRequest("DISABLED",0),actor());
                assertThat(repository.findById(sessionId)).isNotNull();
            });
            assertThat(repository.findById(sessionId)).isNull();
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,id)).isEqualTo(1L);
        } finally { repository.deleteById(sessionId); cleanup(id); }
    }

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.xianshuyuan.scm.auth.service.UserSessionService sessions;
    @Autowired com.xianshuyuan.scm.system.mapper.SystemUserMapper users;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test void committedVersionRejectsStaleSessionWhenRepositoryDeletionFails() throws Exception {
        String name="fallback"+suffix(); long id=fixture(name,false);
        Session stored=repository.createSession();
        stored.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,name);
        repository.save(stored);
        try {
            org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("Simulated session store outage"))
                .when(sessions).invalidateAll(name);
            // Keep the account enabled: rejection must be caused by authVersion, not status.
            service.changeStatus(id,new UserStatusRequest("ENABLED",0),actor());
            assertThat(repository.findById(stored.getId())).isNotNull();
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,id)).isEqualTo(1L);
            var principal=new com.xianshuyuan.scm.auth.security.AuthenticatedUser(id,name,"Test",0,false,false,List.of());
            var details=new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal,null,List.of(),true,true);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details,null,List.of()));
            var request=new org.springframework.mock.web.MockHttpServletRequest();
            var session=new org.springframework.mock.web.MockHttpSession(); request.setSession(session);
            var response=new org.springframework.mock.web.MockHttpServletResponse();
            var chain=new org.springframework.mock.web.MockFilterChain();
            new com.xianshuyuan.scm.auth.security.AccountVersionFilter(users,json).doFilter(request,response,chain);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(session.isInvalid()).isTrue();
            assertThat(chain.getRequest()).isNull();
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally {
            org.mockito.Mockito.reset(sessions);
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            repository.deleteById(stored.getId()); cleanup(id);
        }
    }

    @Test void concurrentDisableAndDeletePreserveOneUsableAdministrator() throws Exception {
        long first=fixture("first"+suffix(),true), second=fixture("second"+suffix(),true);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Boolean> disable=executor.submit(() -> { start.await(); return attempt(() -> service.changeStatus(first,new UserStatusRequest("DISABLED",0),actor())); });
            Future<Boolean> delete=executor.submit(() -> { start.await(); return attempt(() -> service.delete(second,0,actor())); });
            start.countDown();
            assertThat(List.of(disable.get(20,TimeUnit.SECONDS),delete.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("select count(*) from sys_user where id in (?,?) and deleted=false and status='ENABLED'",Long.class,first,second)).isEqualTo(1);
        } finally { executor.shutdownNow(); cleanup(first); cleanup(second); }
    }

    @Test void auditDistinguishesReusedUsernameByImmutablePrincipalId() {
        long target=fixture("audit"+suffix(),false);
        try {
            for(long actorId : List.of(800001L,800002L)) {
                var principal=new com.xianshuyuan.scm.auth.security.AuthenticatedUser(actorId,"reused-name","Actor",0,false,false,List.of());
                var details=new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal,null,List.of(),true,true);
                service.changeStatus(target,new UserStatusRequest("ENABLED",actorId==800001L?0:1),
                    new UsernamePasswordAuthenticationToken(details,null,List.of()));
            }
            assertThat(jdbc.queryForList("select actor_user_id from sys_operation_log where target_type='USER' and target_id=? order by id",Long.class,Long.toString(target)))
                .containsExactly(800001L,800002L);
            assertThat(jdbc.queryForList("select actor_name_snapshot from sys_operation_log where target_type='USER' and target_id=? order by id",String.class,Long.toString(target)))
                .containsExactly("reused-name","reused-name");
        } finally { cleanup(target); }
    }

    private boolean attempt(Runnable operation) {
        try { operation.run(); return true; }
        catch(com.xianshuyuan.scm.common.exception.BusinessException failure) {
            assertThat(failure.getErrorCode()).isEqualTo(SystemErrorCodes.PROTECTED_USER); return false;
        }
    }
    private long fixture(String name,boolean admin) {
        return jdbc.queryForObject("insert into sys_user(username,display_name,status,administrator,password_hash) values(?,?,'ENABLED',?,?) returning id",Long.class,name,"Transaction test",admin,
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("test-password-123"));
    }
    private UsernamePasswordAuthenticationToken actor() { return new UsernamePasswordAuthenticationToken("operator","",List.of(new SimpleGrantedAuthority("system.administrator"))); }
    private String suffix() { return UUID.randomUUID().toString().replace("-","").substring(0,12); }
    private void cleanup(long id) { jdbc.update("delete from sys_operation_log where target_type='USER' and target_id=?",Long.toString(id)); jdbc.update("delete from sys_user where id=?",id); }
}
