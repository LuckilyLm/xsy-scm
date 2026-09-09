package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleCrudIT extends IsolatedUserDatabase {
    private static final String BASE = "/api/system/roles";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.xianshuyuan.scm.auth.service.AuthIdentityService identities;

    @Test void createsNormalizedRolePagesFiltersAndUpdatesWithoutAcceptingSecurityFields() throws Exception {
        String code = "role" + suffix();
        var created = call(post(BASE).content(payload("  " + code.toUpperCase() + "  ", "  Role name  ",
                ",\"systemRole\":true,\"administrator\":true,\"status\":\"DISABLED\",\"version\":99")), "create", 200);
        long id = created.path("id").asLong();
        assertThat(created.path("roleCode").asText()).isEqualTo(code);
        assertThat(created.path("name").asText()).isEqualTo("Role name");
        assertThat(created.path("systemRole").asBoolean()).isFalse();
        assertThat(created.path("status").asText()).isEqualTo("ENABLED");
        assertThat(created.path("version").asInt()).isZero();
        var page = call(get(BASE).param("keyword", code).param("pageSize", "1"), "list", 200);
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("records").get(0).path("id").asLong()).isEqualTo(id);
        assertThat(call(get(BASE).param("keyword", code).param("status", "DISABLED"), "list", 200).path("total").asInt()).isZero();
        call(put(BASE + "/" + id).content(payload(code + "new", "Changed", ",\"version\":0,\"systemRole\":true")), "update", 200);
        var detail = call(get(BASE + "/" + id), "list", 200);
        assertThat(detail.path("roleCode").asText()).isEqualTo(code + "new");
        assertThat(detail.path("systemRole").asBoolean()).isFalse();
        assertThat(detail.path("version").asInt()).isEqualTo(1);
    }

    @Test void normalizedActiveUniquenessAllowsReuseOnlyAfterSoftDeletion() throws Exception {
        String code = "unique" + suffix();
        long first = create(code), second = create("other" + suffix());
        call(post(BASE).content(payload(" " + code.toUpperCase() + " ", "Duplicate", "")), "create", 409);
        call(put(BASE + "/" + second).content(payload(code.toUpperCase(), "Duplicate", ",\"version\":0")), "update", 409);
        call(post(BASE + "/" + first + "/status").content(state("DISABLED", 0)), "status", 200);
        call(post(BASE).content(payload(code, "Still duplicate", "")), "create", 409);
        call(delete(BASE + "/" + first).param("version", "1"), "delete", 200);
        assertThat(create(code)).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("select deleted from sys_role where id=?", Boolean.class, first)).isTrue();
    }

    @Test void versionsStatusAndNotFoundAreExplicit() throws Exception {
        long id = create("version" + suffix());
        call(post(BASE + "/" + id + "/status").content(state("DISABLED", 0)), "status", 200);
        call(put(BASE + "/" + id).content(payload("v" + suffix(), "Changed", ",\"version\":0")), "update", 409);
        call(post(BASE + "/" + id + "/status").content(state("ENABLED", 0)), "status", 409);
        call(delete(BASE + "/" + id).param("version", "0"), "delete", 409);
        long operator = fixtureUser();
        jdbc.update("update sys_user set administrator=true where id=?", operator);
        var principal = identities.loadPrincipal(
                jdbc.queryForObject("select username from sys_user where id=?", String.class, operator));
        var details = new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal, null, List.of(() -> "system.administrator"), true, true);
        mvc.perform(post(BASE + "/" + id + "/status").with(user(details)).with(csrf()).contentType("application/json")
                .content(state("ENABLED", 1))).andExpect(status().isOk());
        call(delete(BASE + "/" + id).param("version", "2"), "delete", 200);
        for (long missing : List.of(id, Long.MAX_VALUE)) {
            call(get(BASE + "/" + missing), "list", 404);
            call(put(BASE + "/" + missing).content(payload("missing", "Missing", ",\"version\":0")), "update", 404);
            call(post(BASE + "/" + missing + "/status").content(state("DISABLED", 0)), "status", 404);
            call(delete(BASE + "/" + missing).param("version", "0"), "delete", 404);
        }
    }

    @Test void entryValidationRejectsMalformedInputs() throws Exception {
        for (String invalid : List.of("{}", payload("a b", "Name", ""), payload("valid", " ", ""),
                payload("x".repeat(101), "Name", ""), payload("valid", "Name", ",\"description\":\"" + "x".repeat(501) + "\"")))
            call(post(BASE).content(invalid), "create", 400);
        long id = create("valid" + suffix());
        call(put(BASE + "/" + id).content(payload("valid", "Name", "")), "update", 400);
        call(post(BASE + "/" + id + "/status").content(state("INVALID", 0)), "status", 400);
        call(post(BASE + "/" + id + "/status").content(state("ENABLED", -1)), "status", 400);
        call(delete(BASE + "/" + id).param("version", "-1"), "delete", 400);
        call(get(BASE + "/0"), "list", 400);
        call(get(BASE).param("page", "0"), "list", 400);
        call(get(BASE).param("pageSize", "101"), "list", 400);
        call(get(BASE).param("status", "INVALID"), "list", 400);
    }

    @Test void exactColonRouteInventoryDeniesBroadPermissionsAndUnimplementedGrants() throws Exception {
        long id = create("routes" + suffix());
        var requests = List.of(get(BASE), get(BASE + "/" + id), post(BASE).content(payload("route" + suffix(), "Role", "")),
                put(BASE + "/" + id).content(payload("edited" + suffix(), "Edited", ",\"version\":0")),
                post(BASE + "/" + id + "/status").content(state("DISABLED", 1)), delete(BASE + "/" + id).param("version", "2"));
        String[] allowed = {"list", "list", "create", "update", "status", "delete"};
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        for (int i = 0; i < requests.size(); i++) {
            for (String denied : List.of("system.manage", "system:user:list", "system:role:read"))
                mvc.perform(requests.get(i).with(user("denied").authorities(() -> denied)).with(csrf()).contentType("application/json"))
                        .andExpect(status().isForbidden());
            call(requests.get(i), allowed[i], 200);
        }
        for (String path : List.of("users", "unknown"))
            mvc.perform(post(BASE + "/1/" + path).with(user("admin").authorities(() -> "system.administrator")).with(csrf()))
                    .andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(user("operator").authorities(() -> "system:role:create")).contentType("application/json").content(payload("csrf", "Role", "")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("select code from sys_permission where code like 'system:role:%' and deleted=false and status='ENABLED'", String.class))
                .containsExactlyInAnyOrder("system:role:list", "system:role:create", "system:role:update", "system:role:status", "system:role:delete", "system:role:assign-permissions", "system:role:assign-menus");
    }

    @Test void reservedPropertiesRequireCentralAdminAndNeverBecomeRoleNameElevation() throws Exception {
        for (String reserved : List.of("admin", "administrator", "system", "root", "superadmin"))
            call(post(BASE).content(payload(reserved, "Reserved", "")), "create", 403);
        long id = create("reserved" + suffix());
        jdbc.update("update sys_role set system_role=true where id=?", id);
        call(put(BASE + "/" + id).content(payload("reservedchanged", "Changed", ",\"version\":0")), "update", 403);
        call(post(BASE + "/" + id + "/status").content(state("DISABLED", 0)), "status", 403);
        call(delete(BASE + "/" + id).param("version", "0"), "delete", 403);
        mvc.perform(put(BASE + "/" + id).with(user("admin").authorities(() -> "system.administrator")).with(csrf()).contentType("application/json")
                .content(payload("reservedchanged", "Admin edited", ",\"version\":0,\"systemRole\":false"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select system_role from sys_role where id=?", Boolean.class, id)).isTrue();
        // Even administrators cannot remove reserved roles through ordinary CRUD.
        mvc.perform(delete(BASE + "/" + id).param("version", "1").with(user("admin").authorities(() -> "system.administrator")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void cannotRemoveOwnCapabilityUnlessAnotherEnabledRoleStillProvidesIt() throws Exception {
        long actorId = jdbc.queryForObject("insert into sys_user(username,display_name,status,must_change_password) values(?,?,'ENABLED',false) returning id", Long.class, "self" + suffix(), "Self");
        String actorName = jdbc.queryForObject("select username from sys_user where id=?", String.class, actorId);
        long role = create("selfrole" + suffix());
        grant(actorId, role, "system:role:status");
        var principal = new com.xianshuyuan.scm.auth.security.AuthenticatedUser(actorId, actorName, "Self", 0, false, false, List.of());
        var details = new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal, null, List.of(() -> "system:role:status"), true, true);
        mvc.perform(post(BASE + "/" + role + "/status").with(user(details)).with(csrf()).contentType("application/json").content(state("DISABLED", 0)))
                .andExpect(status().isForbidden());
        long alternative = create("alternative" + suffix());
        grant(actorId, alternative, "system:role:status");
        mvc.perform(post(BASE + "/" + role + "/status").with(user(details)).with(csrf()).contentType("application/json").content(state("DISABLED", 0)))
                .andExpect(status().isOk());
    }

    @Test void invalidationTouchesActiveAssignmentsOnlyAndAuditUsesImmutableActorId() throws Exception {
        long role = create("affected" + suffix());
        long affected = fixtureUser(), unassigned = fixtureUser(), removed = fixtureUser();
        grant(affected, role, "system:role:list");
        jdbc.update("insert into sys_user_role(user_id,role_id,deleted) values(?,?,true)", removed, role);
        var principal = new com.xianshuyuan.scm.auth.security.AuthenticatedUser(unassigned, username(unassigned), "Actor", 0, true, false, List.of());
        var details = new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal, null, List.of(() -> "system.administrator"), true, true);
        mvc.perform(post(BASE + "/" + role + "/status").with(user(details)).with(csrf()).contentType("application/json").content(state("DISABLED", 0))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, affected)).isEqualTo(1);
        for (long untouched : List.of(unassigned, removed))
            assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?", Long.class, untouched)).isZero();
        assertThat(jdbc.queryForObject("select actor_user_id from sys_operation_log where target_type='ROLE' and target_id=? and operation_code='ROLE_STATUS'", Long.class, Long.toString(role))).isEqualTo(unassigned);
        assertThat(jdbc.queryForObject("select before_data->>'status' from sys_operation_log where target_type='ROLE' and target_id=? and operation_code='ROLE_STATUS'", String.class, Long.toString(role))).isEqualTo("ENABLED");
        assertThat(jdbc.queryForObject("select count(*) from sys_user_role where role_id=?", Long.class, role)).isEqualTo(2);
    }

    private void grant(long userId, long roleId, String permission) {
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", userId, roleId);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code=? and deleted=false", roleId, permission);
    }
    private long fixtureUser() { return jdbc.queryForObject("insert into sys_user(username,display_name,status,must_change_password) values(?,'Fixture','ENABLED',false) returning id", Long.class, "fixture" + suffix()); }
    private String username(long id) { return jdbc.queryForObject("select username from sys_user where id=?", String.class, id); }
    private long create(String code) throws Exception { return call(post(BASE).content(payload(code, "Role", "")), "create", 200).path("id").asLong(); }
    private JsonNode call(MockHttpServletRequestBuilder request, String permission, int expected) throws Exception {
        return json.readTree(mvc.perform(request.with(user("operator").authorities(() -> "system:role:" + permission)).with(csrf()).contentType("application/json"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }
    private String payload(String code, String name, String extra) { return "{\"roleCode\":\"" + code + "\",\"name\":\"" + name + "\"" + extra + "}"; }
    private String state(String status, int version) { return "{\"status\":\"" + status + "\",\"version\":" + version + "}"; }
    private String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
