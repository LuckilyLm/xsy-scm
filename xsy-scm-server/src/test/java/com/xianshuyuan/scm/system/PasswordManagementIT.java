package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.auth.dto.ChangePasswordRequest;
import com.xianshuyuan.scm.auth.security.AuthenticatedUser;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.PasswordManagementService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.xianshuyuan.scm.system.dto.ResetPasswordRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PasswordManagementIT extends IsolatedUserDatabase {
    @Autowired PasswordManagementService passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session> sessionsRepository;
    @MockitoSpyBean UserSessionService sessions;
    @Autowired SystemUserMapper users;
    @Autowired ObjectMapper objectMapper;

    @Test
    void directServiceRejectsPasswordBelowTwelveUtf8Bytes() {
        long id = fixture("pwshort" + suffix(), false, "Current-Password-123");
        try {
            assertThatThrownBy(() -> passwords.changeOwn(
                    new ChangePasswordRequest("Current-Password-123", "Ab1!", 0), principal(id, false)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SystemErrorCodes.PASSWORD_POLICY);
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(id))).isZero();
        } finally { cleanup(id); }
    }

    @Test
    void administratorResetUsesDedicatedColonAuthorityAndKeepsSamePasswordRejection() {
        long admin = fixture("pwadmin" + suffix(), true, "Admin-Password-123");
        long target = fixture("pwtarget" + suffix(), false, "Target-Password-123");
        try {
            Authentication actor = principal(admin, true, "system:user:reset-password");
            assertThatThrownBy(() -> passwords.reset(target,
                    new ResetPasswordRequest("Target-Password-123", 0), actor))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SystemErrorCodes.PASSWORD_REUSED);
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, target)).isZero();
        } finally { cleanup(admin); cleanup(target); }
    }

    @Test
    void selfChangeRejectsWrongCurrentSamePasswordAndVersion() {
        long id = fixture("pwself" + suffix(), false, "Current-Password-123");
        try {
            assertError(() -> passwords.changeOwn(new ChangePasswordRequest("Wrong-Password-123", "New-Password-123!", 0), principal(id, false)), SystemErrorCodes.PASSWORD_CURRENT_INVALID);
            assertError(() -> passwords.changeOwn(new ChangePasswordRequest("Current-Password-123", "Current-Password-123", 0), principal(id, false)), SystemErrorCodes.PASSWORD_REUSED);
            assertError(() -> passwords.changeOwn(new ChangePasswordRequest("Current-Password-123", "New-Password-123!", 1), principal(id, false)), com.xianshuyuan.scm.common.exception.ErrorCode.DATA_CONFLICT);
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id)).isZero();
        } finally { cleanup(id); }
    }

    @Test
    void resetRequiresDatabaseAdministratorAndRejectsDisabledTarget() {
        long actor = fixture("pwactor" + suffix(), false, "Admin-Password-123");
        long target = fixture("pwdisabled" + suffix(), true, "Target-Password-123");
        try {
            assertError(() -> passwords.reset(target, new ResetPasswordRequest("New-Password-123!", 0), principal(actor, false, "system:user:reset-password")), com.xianshuyuan.scm.auth.AuthErrorCodes.PERMISSION_DENIED);
            long administrator = fixture("pwadmin2" + suffix(), true, "Administrator-Password-123");
            try {
                jdbc.update("update sys_user set status='DISABLED' where id=?", target);
                assertError(() -> passwords.reset(target, new ResetPasswordRequest("New-Password-123!", 0), principal(administrator, true, "system:user:reset-password")), SystemErrorCodes.PASSWORD_TARGET_UNUSABLE);
            } finally { cleanup(administrator); }
        } finally { cleanup(actor); cleanup(target); }
    }


    @Test
    void passwordChangeRollsBackAuditAndAuthVersionTogether() {
        long id = fixture("pwrollback" + suffix(), false, "Current-Password-123");
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                passwords.changeOwn(new ChangePasswordRequest("Current-Password-123", "New-Password-123!", 0), principal(id, false));
                assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(id))).isEqualTo(1L);
                status.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(id))).isZero();
        } finally { cleanup(id); }
    }

    @Test
    void committedPasswordChangeKeepsAuthVersionAuthoritativeWhenSessionStoreFails() {
        long id = fixture("pwfallback" + suffix(), false, "Current-Password-123");
        try {
            Mockito.doThrow(new DataAccessResourceFailureException("session store outage")).when(sessions).invalidateAll(Mockito.anyString());
            passwords.changeOwn(new ChangePasswordRequest("Current-Password-123", "New-Password-123!", 0), principal(id, false));
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(id))).isEqualTo(1L);
            Mockito.verify(sessions).invalidateAll(Mockito.anyString());
        } finally { Mockito.reset(sessions); cleanup(id); }
    }
    @Test
    void concurrentPasswordChangesAllowOnlyOneOptimisticVersionWinner() throws Exception {
        long id = fixture("pwconcurrent" + suffix(), false, "Current-Password-123");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> attemptChange(id, "New-Password-123!", start));
            Future<Boolean> second = executor.submit(() -> attemptChange(id, "Other-Password-123!", start));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, id)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(id))).isEqualTo(1L);
        } finally { executor.shutdownNow(); cleanup(id); }
    }

    private boolean attemptChange(long id, String password, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            passwords.changeOwn(new ChangePasswordRequest("Current-Password-123", password, 0), principal(id, false));
            return true;
        } catch (BusinessException failure) {
            assertThat(failure.getErrorCode()).isEqualTo(com.xianshuyuan.scm.common.exception.ErrorCode.DATA_CONFLICT);
            return false;
        }
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, com.xianshuyuan.scm.common.exception.ErrorCode code) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
    private Authentication principal(long id, boolean administrator, String... authorities) {
        var user = new AuthenticatedUser(id, "actor", "Actor", 0, administrator, false, List.of());
        var details = new SystemUserDetails(user, null,
                java.util.Arrays.stream(authorities).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList(), true, true);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private long fixture(String username, boolean administrator, String password) {
        return jdbc.queryForObject("insert into sys_user(username,display_name,status,administrator,password_hash,must_change_password) values(?,?,'ENABLED',?,?,false) returning id",
                Long.class, username, "Password test", administrator,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
    }

    private String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private void cleanup(long id) {
        jdbc.update("delete from sys_operation_log where target_type='USER' and target_id=?", Long.toString(id));
        jdbc.update("delete from sys_user where id=?", id);
    }
}
