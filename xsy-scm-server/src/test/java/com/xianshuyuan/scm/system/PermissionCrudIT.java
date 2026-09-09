package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.JsonNode;
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
class PermissionCrudIT extends IsolatedUserDatabase {
    private static final String BASE = "/api/system/permissions";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthIdentityService identities;

    @Test void normalizedCrudUsesSafeFieldsAndStableCodeIdentity() throws Exception {
        var actor = admin();
        String code = "catalog:item:" + suffix();
        var created = call(post(BASE).content(payload(" " + code.toUpperCase() + " ", "  Capability  ",
                ",\"systemPermission\":true,\"status\":\"ENABLED\",\"version\":99")), actor, 200);
        long id = created.path("id").asLong();
        assertThat(created.path("permissionCode").asText()).isEqualTo(code);
        assertThat(created.path("name").asText()).isEqualTo("Capability");
        assertThat(created.path("type").asText()).isEqualTo("ACTION");
        assertThat(created.path("systemPermission").asBoolean()).isFalse();
        assertThat(created.path("status").asText()).isEqualTo("DISABLED");
        assertThat(created.path("version").asInt()).isZero();
        var page = call(get(BASE).param("keyword", code).param("module", "catalog").param("type", "ACTION").param("pageSize", "1"), actor, 200);
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(page.path("records").get(0).path("id").asLong()).isEqualTo(id);
        assertThat(call(get(BASE).param("keyword", code).param("status", "ENABLED"), actor, 200).path("total").asLong()).isZero();
        call(put(BASE + "/" + id).content(payload(code, "Changed", ",\"version\":0,\"systemPermission\":true")), actor, 200);
        var detail = call(get(BASE + "/" + id), actor, 200);
        assertThat(detail.path("name").asText()).isEqualTo("Changed");
        assertThat(detail.path("version").asInt()).isEqualTo(1);
        assertThat(detail.path("systemPermission").asBoolean()).isFalse();
        call(put(BASE + "/" + id).content(payload(code + "new", "Escalation", ",\"version\":1")), actor, 403);
        call(post(BASE + "/" + id + "/status").content(state("ENABLED", 1)), actor, 200);
        call(delete(BASE + "/" + id).param("version", "2"), actor, 200);
        assertThat(jdbc.queryForObject("select deleted from sys_permission where id=?", Boolean.class, id)).isTrue();
        call(get(BASE + "/" + id), actor, 404);
        var reused = call(post(BASE).content(payload(code, "New identity", "")), actor, 200);
        assertThat(reused.path("id").asLong()).isNotEqualTo(id);
        assertThat(reused.path("status").asText()).isEqualTo("DISABLED");
    }

    @Test void uniquenessVersionsValidationAndNotFoundAreExplicit() throws Exception {
        var actor = admin(); String code = "catalog:item:" + suffix(); long id = create(code, actor);
        call(post(BASE).content(payload(" " + code.toUpperCase() + " ", "Duplicate", "")), actor, 409);
        call(post(BASE + "/" + id + "/status").content(state("ENABLED", 0)), actor, 200);
        call(put(BASE + "/" + id).content(payload(code, "Stale", ",\"version\":0")), actor, 409);
        call(post(BASE + "/" + id + "/status").content(state("DISABLED", 0)), actor, 409);
        call(delete(BASE + "/" + id).param("version", "0"), actor, 409);
        for (String invalid : List.of("{}", payload("bad code", "Name", ""), payload("nocolons", "Name", ""),
                payload("catalog:item:" + "x".repeat(160), "Name", ""), payload(code, " ", ""),
                payload(code, "Name", "").replace("ACTION", "UNKNOWN"))) call(post(BASE).content(invalid), actor, 400);
        call(put(BASE + "/" + id).content(payload(code, "Missing version", "")), actor, 400);
        call(post(BASE + "/" + id + "/status").content(state("INVALID", 1)), actor, 400);
        call(delete(BASE + "/" + id).param("version", "-1"), actor, 400);
        call(get(BASE + "/0"), actor, 400);
        call(get(BASE).param("page", "0"), actor, 400);
        call(get(BASE).param("pageSize", "101"), actor, 400);
        call(get(BASE).param("type", "INVALID"), actor, 400);
        call(get(BASE).param("status", "INVALID"), actor, 400);
        call(get(BASE + "/" + Long.MAX_VALUE), actor, 404);
        call(put(BASE + "/" + Long.MAX_VALUE).content(payload(code, "Missing", ",\"version\":0")), actor, 404);
        call(post(BASE + "/" + Long.MAX_VALUE + "/status").content(state("ENABLED", 0)), actor, 404);
        call(delete(BASE + "/" + Long.MAX_VALUE).param("version", "0"), actor, 404);
    }

    @Test void reservedCoreAndMenuDependenciesCannotBeRewrittenOrRemoved() throws Exception {
        var actor = admin();
        call(post(BASE).content(payload("system:invented:grant", "Reserved", "")), actor, 403);
        long core = jdbc.queryForObject("select id from sys_permission where code='system:permission:list' and deleted=false", Long.class);
        call(delete(BASE + "/" + core).param("version", "0"), actor, 403);
        call(post(BASE + "/" + core + "/status").content(state("DISABLED", 0)), actor, 403);
        call(put(BASE + "/" + core).content(payload("system:permission:list", "Display label", ",\"version\":0")), actor, 200);
        String code = "catalog:menu:" + suffix(); long id = create(code, actor);
        jdbc.update("insert into sys_menu(type,name,required_permission_code,status) values('DIRECTORY','Linked',?,'DISABLED')", code);
        call(delete(BASE + "/" + id).param("version", "0"), actor, 400);
        String orphan = "catalog:orphan:" + suffix();
        jdbc.update("insert into sys_menu(type,name,required_permission_code) values('DIRECTORY','Orphan',?)", orphan);
        call(post(BASE).content(payload(orphan, "Implicit menu reactivation", "")), actor, 403);
    }

    @Test void exactRoutesCsrfAndDatabaseActorRevalidationPreventBypass() throws Exception {
        var actor = admin(); String code = "catalog:item:" + suffix(); long id = create(code, actor);
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        var requests = List.of(get(BASE), get(BASE + "/" + id), post(BASE).content(payload("catalog:new:" + suffix(), "New", "")),
                put(BASE + "/" + id).content(payload(code, "Changed", ",\"version\":0")),
                post(BASE + "/" + id + "/status").content(state("ENABLED", 1)), delete(BASE + "/" + id).param("version", "2"));
        for (var request : requests) for (String denied : List.of("system.manage", "system:role:list", "system:permission:read"))
            mvc.perform(request.with(user("denied").authorities(() -> denied)).with(csrf()).contentType("application/json")).andExpect(status().isForbidden());
        for (String path : List.of("roles", "menus", "grant", "unknown"))
            call(post(BASE + "/1/" + path), actor, 403);
        mvc.perform(post(BASE).with(user(actor)).contentType("application/json").content(payload("catalog:csrf:" + suffix(), "CSRF", ""))).andExpect(status().isForbidden());
        var operator = operator("create", "update", "status", "delete", "list");
        long ordinary = create("catalog:ordinary:" + suffix(), operator);
        call(get(BASE + "/" + ordinary), operator, 200);
        call(post(BASE + "/" + ordinary + "/status").content(state("ENABLED", 0)), operator, 200);
        call(delete(BASE + "/" + ordinary).param("version", "1"), operator, 200);
        // A stale administrator flag must not bypass current database checks even if authVersion was not bumped.
        jdbc.update("update sys_user set administrator=false where id=?", actor.getUser().userId());
        call(post(BASE).content(payload("catalog:stale:" + suffix(), "Stale", "")), actor, 403);
        assertThat(jdbc.queryForList("select code from sys_permission where code like 'system:permission:%' and deleted=false and status='ENABLED'", String.class))
                .containsExactlyInAnyOrder("system:permission:list", "system:permission:create", "system:permission:update", "system:permission:status", "system:permission:delete");
    }

    @Test void legacyCustomIdentityIsNotCoreButRegisteredLegacyCapabilitiesRemainProtected() throws Exception {
        var actor = admin();
        long custom = jdbc.queryForObject("insert into sys_permission(code,name,module,resource_type) values(?,'Legacy','catalog','API') returning id", Long.class, "custom." + suffix());
        call(post(BASE + "/" + custom + "/status").content(state("DISABLED", 0)), actor, 200);
        call(delete(BASE + "/" + custom).param("version", "1"), actor, 200);
        long core = jdbc.queryForObject("insert into sys_permission(code,name,module,resource_type) values('product.read','Registered','product','API') returning id", Long.class);
        call(post(BASE + "/" + core + "/status").content(state("DISABLED", 0)), actor, 403);
        call(delete(BASE + "/" + core).param("version", "0"), actor, 403);
    }

    private SystemUserDetails admin() { return identity(true); }
    private SystemUserDetails operator(String... operations) {
        String name = "permissionuser" + suffix();
        long userId = jdbc.queryForObject("insert into sys_user(username,display_name,status,administrator,must_change_password) values(?,'Fixture','ENABLED',false,false) returning id", Long.class, name);
        long role = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Operator') returning id", Long.class, "operator" + suffix());
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", userId, role);
        for (String operation : operations) jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code=? and deleted=false", role, "system:permission:" + operation);
        return details(name);
    }
    private SystemUserDetails identity(boolean admin) {
        String name = "permissionuser" + suffix();
        jdbc.update("insert into sys_user(username,display_name,status,administrator,must_change_password) values(?,'Fixture','ENABLED',?,false)", name, admin);
        return details(name);
    }
    private SystemUserDetails details(String name) {
        var principal = identities.loadPrincipal(name);
        return new SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
    }
    private long create(String code, SystemUserDetails actor) throws Exception { return call(post(BASE).content(payload(code, "Capability", "")), actor, 200).path("id").asLong(); }
    private JsonNode call(MockHttpServletRequestBuilder request, SystemUserDetails actor, int expected) throws Exception {
        return json.readTree(mvc.perform(request.with(user(actor)).with(csrf()).contentType("application/json")).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString()).path("data");
    }
    private String payload(String code, String name, String extra) { return "{\"permissionCode\":\"" + code + "\",\"name\":\"" + name + "\",\"type\":\"ACTION\",\"module\":\"catalog\"" + extra + "}"; }
    private String state(String status, int version) { return "{\"status\":\"" + status + "\",\"version\":" + version + "}"; }
    private String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
