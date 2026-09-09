package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.AuthIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserRoleGrantsIT extends IsolatedUserDatabase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthIdentityService identities;

    @Test void replacementPreservesRetainedIdentityAndSoftDeletedHistory() throws Exception {
        long actorId = account(true), target = account(false), retained = role(), removed = role(), added = role();
        var actor = identity(actorId);
        long retainedId = association(target, retained), removedId = association(target, removed);
        mvc.perform(get(path(target)).with(user(actor))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.roles.length()").value(2));
        assign(target, List.of(retained, added), 0, actor, 200);
        assertThat(jdbc.queryForObject("select id from sys_user_role where user_id=? and role_id=? and deleted=false", Long.class, target, retained)).isEqualTo(retainedId);
        assertThat(jdbc.queryForObject("select deleted from sys_user_role where id=?", Boolean.class, removedId)).isTrue();
        assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, target)).isEqualTo(1);
        assign(target, List.of(added, retained), 1, actor, 200);
        assertThat(jdbc.queryForObject("select version from sys_user where id=?", Integer.class, target)).isEqualTo(1);
        assign(target, List.of(), 1, actor, 200);
        assertThat(jdbc.queryForObject("select count(*) from sys_user_role where user_id=? and deleted=false", Long.class, target)).isZero();
    }

    @Test void invalidReplacementNeverPartiallyChangesRelationsVersionsOrAudit() throws Exception {
        var actor = identity(account(true)); long target = account(false), retained = role(), disabled = role();
        association(target, retained);
        jdbc.update("update sys_role set status='DISABLED' where id=?", disabled);
        assign(target, List.of(retained, retained), 0, actor, 400);
        assign(target, List.of(retained, Long.MAX_VALUE), 0, actor, 404);
        assign(target, List.of(disabled), 0, actor, 400);
        assign(target, List.of(retained), 9, actor, 409);
        jdbc.update("update sys_role set deleted=true where id=?", disabled);
        assign(target, List.of(disabled), 0, actor, 404);
        assertThat(jdbc.queryForObject("select count(*) from sys_user_role where user_id=? and deleted=false", Long.class, target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, target)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(target))).isZero();
        jdbc.update("update sys_user set status='DISABLED' where id=?", target);
        assign(target, List.of(), 0, actor, 200);
        jdbc.update("update sys_user set deleted=true where id=?", target);
        assign(target, List.of(), 0, actor, 404);
        assign(Long.MAX_VALUE, List.of(), 0, actor, 404);
    }

    @Test void unmatchedRoutesCsrfAndMalformedRequestsFailClosed() throws Exception {
        var actor = identity(account(true)); long target = account(false);
        mvc.perform(get(path(target))).andExpect(status().isUnauthorized());
        for (String authority : List.of("system.manage", "system:user:update", "system:role:list")) {
            mvc.perform(post(path(target)).with(user("wrong").authorities(() -> authority)).with(csrf())
                    .contentType("application/json").content("{\"roleIds\":[],\"version\":0}")).andExpect(status().isForbidden());
        }
        mvc.perform(post(path(target)).with(user(actor)).contentType("application/json")
                .content("{\"roleIds\":[],\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(put(path(target)).with(user(actor)).with(csrf())).andExpect(status().isForbidden());
        for (String payload : List.of("{}", "{\"roleIds\":[null],\"version\":0}",
                "{\"roleIds\":[0],\"version\":0}", "{\"roleIds\":[],\"version\":-1}"))
            mvc.perform(post(path(target)).with(user(actor)).with(csrf()).contentType("application/json").content(payload))
                    .andExpect(status().isBadRequest());
        assign(target, java.util.Collections.nCopies(101, 1L), 0, actor, 400);
    }

    @Test void replacementWriteCreatesNewHistoryWhenReadding() throws Exception {
        var actor = identity(account(true)); long target = account(false), role = role();
        assign(target, List.of(role), 0, actor, 200);
        long first = jdbc.queryForObject("select id from sys_user_role where user_id=? and deleted=false", Long.class, target);
        assign(target, List.of(), 1, actor, 200);
        assign(target, List.of(role), 2, actor, 200);
        assertThat(jdbc.queryForObject("select id from sys_user_role where user_id=? and deleted=false", Long.class, target)).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from sys_operation_log where target_type='USER' and target_id=?", Long.class, Long.toString(target))).isEqualTo(3);
    }

    @Test void staleDatabaseAdministratorCannotWrite() throws Exception {
        var actor = identity(account(true)); long target = account(false);
        jdbc.update("update sys_user set administrator=false where id=?", actor.getUser().userId());
        assign(target, List.of(role()), 0, actor, 403);
    }

    @Test void delegatedAssignmentRequiresFinalPermissionAndNavigationCoverage() throws Exception {
        long operator = account(false), authorityRole = role(), target = account(false), safe = role(), excessive = role();
        association(operator, authorityRole);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:user:assign-roles' and deleted=false", authorityRole);
        var actor = identity(operator);
        assign(target, List.of(safe), 0, actor, 200);
        long permission = jdbc.queryForObject("insert into sys_permission(code,name,module,resource_type,status) values(?,'Future','catalog','API','DISABLED') returning id", Long.class, "future:" + suffix());
        jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)", excessive, permission);
        assign(target, List.of(excessive), 1, actor, 403);
        long menu = jdbc.queryForObject("insert into sys_menu(type,name) values('DIRECTORY','Navigation') returning id", Long.class);
        jdbc.update("insert into sys_role_menu(role_id,menu_id) values(?,?)", safe, menu);
        assign(target, List.of(safe), 1, actor, 403);
        jdbc.update("insert into sys_role_menu(role_id,menu_id) values(?,?)", authorityRole, menu);
        assign(target, List.of(safe), 1, actor, 200);
        assign(operator, List.of(safe), 0, actor, 403);
    }

    @Test void delegatedOperatorCannotRemoveRetainedReservedRole() throws Exception {
        long operator = account(false), authorityRole = role(), target = account(false), reserved = role();
        association(operator, authorityRole); association(target, reserved);
        jdbc.update("update sys_role set system_role=true where id=?", reserved);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:user:assign-roles' and deleted=false", authorityRole);
        assign(target, List.of(), 0, identity(operator), 403);
    }

    private void assign(long target, List<Long> roles, int version, SystemUserDetails actor, int expected) throws Exception {
        mvc.perform(post(path(target)).with(user(actor)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("roleIds", roles, "version", version))))
                .andExpect(status().is(expected));
    }
    private long account(boolean administrator) {
        return jdbc.queryForObject("insert into sys_user(username,display_name,administrator,must_change_password) values(?,'Grant user',?,false) returning id", Long.class, "grant" + suffix(), administrator);
    }
    private long role() {
        return jdbc.queryForObject("insert into sys_role(code,name) values(?,'Grant role') returning id", Long.class, "grant" + suffix());
    }
    private long association(long user, long role) {
        return jdbc.queryForObject("insert into sys_user_role(user_id,role_id) values(?,?) returning id", Long.class, user, role);
    }
    private SystemUserDetails identity(long id) {
        String username = jdbc.queryForObject("select username from sys_user where id=?", String.class, id);
        var principal = identities.loadPrincipal(username);
        return new SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
    }
    private static String path(long id) { return "/api/system/users/" + id + "/roles"; }
    private static String suffix() { return UUID.randomUUID().toString().replace("-", ""); }
}
