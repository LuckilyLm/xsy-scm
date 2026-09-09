package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.DepartmentService;
import com.xianshuyuan.scm.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DepartmentTransactionsIT extends IsolatedUserDatabase {
    @Autowired DepartmentService departments;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @ParameterizedTest
    @ValueSource(strings={"disable","delete","move"})
    void assignmentFirstSerializesDepartmentMutation(String command) throws Exception {
        long department=department(), parent=department(), user=user();
        var executor=Executors.newSingleThreadExecutor();
        AtomicInteger waitingPid=new AtomicInteger();
        try {
            var tx=new TransactionTemplate(manager);
            Future<Boolean> future=tx.execute(status -> {
                users.update(user,new UpdateUserRequest("Assigned",department,null,null,0),actor());
                Future<Boolean> pending=executor.submit(() -> transactionAttempt(waitingPid,()->command(command,department,parent)));
                awaitAdvisoryWait(waitingPid);
                assertThat(pending.isDone()).isFalse();
                return pending;
            });
            assertThat(future.get(15,TimeUnit.SECONDS)).isEqualTo(command.equals("move"));
            assertThat(jdbc.queryForObject("select department_id from sys_user where id=?",Long.class,user)).isEqualTo(department);
            assertThat(jdbc.queryForObject("select deleted from sys_department where id=?",Boolean.class,department)).isFalse();
            assertThat(jdbc.queryForObject("select status from sys_department where id=?",String.class,department)).isEqualTo("ENABLED");
        } finally { executor.shutdownNow(); cleanup(user,department,parent); }
    }

    @ParameterizedTest
    @ValueSource(strings={"disable","delete","move"})
    void departmentMutationFirstSerializesAssignment(String command) throws Exception {
        long department=department(), parent=department(), user=user();
        var executor=Executors.newSingleThreadExecutor();
        AtomicInteger waitingPid=new AtomicInteger();
        try {
            var tx=new TransactionTemplate(manager);
            Future<Boolean> future=tx.execute(status -> {
                command(command,department,parent);
                Future<Boolean> pending=executor.submit(() -> transactionAttempt(waitingPid,
                    ()->users.update(user,new UpdateUserRequest("Assigned",department,null,null,0),actor())));
                awaitAdvisoryWait(waitingPid);
                assertThat(pending.isDone()).isFalse();
                return pending;
            });
            assertThat(future.get(15,TimeUnit.SECONDS)).isEqualTo(command.equals("move"));
            assertThat(jdbc.queryForObject("select department_id from sys_user where id=?",Long.class,user))
                .isEqualTo(command.equals("move")?department:null);
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?",Long.class,Long.toString(user)))
                .isEqualTo(command.equals("move")?1L:0L);
        } finally { executor.shutdownNow(); cleanup(user,department,parent); }
    }

    @Test void rollbackRemovesDepartmentWriteAndAuditTogether() {
        long id=department(), parent=department();
        try {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                command("move",id,parent);
                assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='DEPARTMENT' and target_id=?",Long.class,Long.toString(id))).isEqualTo(1L);
                status.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("select parent_id from sys_department where id=?",Long.class,id)).isNull();
            assertThat(jdbc.queryForObject("select version from sys_department where id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='DEPARTMENT' and target_id=?",Long.class,Long.toString(id))).isZero();
        } finally { cleanup(-1,id,parent); }
    }

    @Test void auditInsertFailureRollsBackDepartmentMutation() {
        long id=department();
        try {
            // A transaction-local constraint failure simulates unavailable audit storage.
            jdbc.execute("alter table sys_operation_log add constraint department_test_audit_failure check(target_type <> 'DEPARTMENT') not valid");
            assertThatThrownBy(()->departments.changeStatus(id,new DepartmentStatusRequest("DISABLED",0),actor()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("select status from sys_department where id=?",String.class,id)).isEqualTo("ENABLED");
            assertThat(jdbc.queryForObject("select version from sys_department where id=?",Integer.class,id)).isZero();
        } finally {
            jdbc.execute("alter table sys_operation_log drop constraint if exists department_test_audit_failure");
            cleanup(-1,id);
        }
    }

    private boolean transactionAttempt(AtomicInteger pid,Runnable action) {
        try {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                pid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                action.run();
            });
            return true;
        } catch (BusinessException failure) {
            assertThat(failure.getErrorCode()).isIn(SystemErrorCodes.INVALID_DEPARTMENT,SystemErrorCodes.DEPARTMENT_IN_USE);
            return false;
        }
    }

    private void awaitAdvisoryWait(AtomicInteger pid) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            if(pid.get()!=0 && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pg_locks where pid=? and locktype='advisory' and not granted)",Boolean.class,pid.get()))) return;
            try { Thread.sleep(20); }
            catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        }
        throw new AssertionError("Expected competing transaction to wait on shared security advisory lock");
    }

    private void command(String command,long department,long parent) {
        switch(command) {
            case "disable" -> departments.changeStatus(department,new DepartmentStatusRequest("DISABLED",0),actor());
            case "delete" -> departments.delete(department,0,actor());
            case "move" -> departments.update(department,new UpdateDepartmentRequest("moved"+suffix(),"Moved",parent,0,0),actor());
            default -> throw new IllegalArgumentException(command);
        }
    }
    private long department() {
        return jdbc.queryForObject("insert into sys_department(code,name) values(?,'Transaction') returning id",Long.class,"tx"+suffix());
    }
    private long user() {
        return jdbc.queryForObject("insert into sys_user(username,display_name) values(?,'Transaction') returning id",Long.class,"txuser"+suffix());
    }
    private UsernamePasswordAuthenticationToken actor() {
        return new UsernamePasswordAuthenticationToken("operator","",List.of());
    }
    private String suffix() { return UUID.randomUUID().toString().replace("-","").substring(0,12); }
    private void cleanup(long user,long... departments) {
        jdbc.update("delete from sys_operation_log where target_type='USER' and target_id=?",Long.toString(user));
        jdbc.update("delete from sys_user where id=?",user);
        for(long id:departments) {
            jdbc.update("delete from sys_operation_log where target_type='DEPARTMENT' and target_id=?",Long.toString(id));
            jdbc.update("delete from sys_department where id=?",id);
        }
    }
}
