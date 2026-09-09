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
class MenuCrudIT extends IsolatedUserDatabase {
    static final String BASE = "/api/system/menus";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthIdentityService identities;
    @Autowired com.xianshuyuan.scm.system.service.MenuService service;

    @Test void namesBeyondDatabaseBoundaryAreRejectedAtEntry() throws Exception {
        var actor = admin(); long root = create(directory(null, "Valid", ""), actor);
        call(post(BASE).content(directory(null, "x".repeat(101), "")), actor, 400);
        call(put(BASE + "/" + root).content(directory(null, "x".repeat(101), ",\"version\":0")), actor, 400);
    }

    @Test void deepValidTreeSerializesAsSuccessfulHttpResponse() throws Exception {
        var actor = admin();
        jdbc.update("insert into sys_menu(id,parent_id,type,name) select 2000000+n,case when n=1 then null else 1999999+n end,'DIRECTORY','Deep HTTP' from generate_series(1,64) n");
        var result = mvc.perform(get(BASE + "/tree").with(user(actor))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body;
        try (var parser = json.createParser(result.getResponse().getContentAsString())) {
            body = json.readTree(parser);
            assertThat(parser.nextToken()).as("no trailing JSON or error envelope").isNull();
        }
        assertThat(body.path("code").asInt()).isEqualTo(0);
        assertThat(body.path("data").size()).isEqualTo(1);
        var node = body.path("data").get(0); int depth = 1;
        while (!node.path("children").isEmpty()) {
            assertThat(node.path("children").size()).isEqualTo(1);
            node = node.path("children").get(0); depth++;
        }
        assertThat(depth).isEqualTo(64);
    }

    @Test void legacyDeepTreesReturnCompleteControlledErrorsBeforeFiltering() throws Exception {
        var actor = admin();
        for (int depth : List.of(600, 4000)) {
            jdbc.update("insert into sys_menu(id,parent_id,type,name,status,visible) select 1000000+n,case when n=1 then null else 999999+n end,'DIRECTORY','Deep','DISABLED',false from generate_series(1,?) n", depth);
            for (var request : List.of(get(BASE + "/tree"), get(BASE + "/tree").param("status", "ENABLED").param("visible", "true"))) {
                var result = mvc.perform(request.with(user(actor))).andExpect(status().isBadRequest()).andReturn();
                try (var parser = json.createParser(result.getResponse().getContentAsString())) {
                    JsonNode body = json.readTree(parser);
                    assertThat(body.path("code").asInt()).isEqualTo(40045);
                    assertThat(parser.nextToken()).isNull();
                }
            }
            jdbc.update("delete from sys_menu where id between 1000001 and 1004000");
        }
    }

    @Test void depthBoundaryChecksFullSubtreeButAllowsLegacyRepairAndUnrelatedWrites() throws Exception {
        var actor = admin();
        jdbc.update("insert into sys_menu(id,parent_id,type,name) select 3000000+n,case when n=1 then null else 2999999+n end,'DIRECTORY','Chain' from generate_series(1,65) n");
        call(get(BASE + "/3000065"), actor, 200);
        call(get(BASE), actor, 200);
        long independent = create(directory(null, "Independent", ""), actor);
        call(post(BASE + "/3000001/status").content(state("DISABLED", 0)), actor, 200);
        call(put(BASE + "/3000001").content(directory(null, "Metadata", ",\"version\":1")), actor, 200);
        call(put(BASE + "/3000002").content(directory(null, "Repair", ",\"version\":0")), actor, 200);
        call(get(BASE + "/tree"), actor, 200);
        call(post(BASE).content(directory(3000065L, "Too deep", "")), actor, 400);
        jdbc.update("update sys_menu set status='DISABLED',visible=false where id=3000065");
        call(put(BASE + "/3000002").content(directory(independent, "Too deep subtree", ",\"version\":1")), actor, 400);
        assertThat(call(get(BASE + "/3000002"), actor, 200).path("version").asInt()).isEqualTo(1);
        call(delete(BASE + "/" + independent).param("version", "0"), actor, 200);
    }

    @Test void crudPreservesIdentityAndBuildsOrderedTree() throws Exception {
        var actor = admin();
        long root = create(directory(null, " Root ", ""), actor);
        long leaf = create(menu(root, "Products", "products", "/products", ""), actor);
        var detail = call(get(BASE + "/" + root), actor, 200);
        assertThat(detail.path("name").asText()).isEqualTo("Root");
        assertThat(detail.path("type").asText()).isEqualTo("DIRECTORY");
        assertThat(call(get(BASE).param("keyword", "Products").param("pageSize", "1"), actor, 200).path("total").asInt()).isEqualTo(1);
        assertThat(call(get(BASE + "/tree"), actor, 200).get(0).path("children").get(0).path("id").asLong()).isEqualTo(leaf);
        call(put(BASE + "/" + leaf).content(menu(root, "Updated", "products", "/products", ",\"version\":0")), actor, 200);
        assertThat(call(get(BASE + "/" + leaf), actor, 200).path("version").asInt()).isEqualTo(1);
        call(post(BASE + "/" + root + "/status").content(state("DISABLED", 0)), actor, 200);
        assertThat(call(get(BASE + "/tree").param("status", "ENABLED"), actor, 200)).isEmpty();
        call(delete(BASE + "/" + root).param("version", "1"), actor, 400);
        call(delete(BASE + "/" + leaf).param("version", "1"), actor, 200);
        call(get(BASE + "/" + leaf), actor, 404);
        assertThat(jdbc.queryForObject("select deleted from sys_menu where id=?", Boolean.class, leaf)).isTrue();
        long reused = create(menu(null, "Reused", "products", "/products", ""), actor);
        assertThat(reused).isNotEqualTo(leaf);
        call(delete(BASE + "/" + root).param("version", "1"), actor, 200);
    }

    @Test void validatesMetadataVersionsUniquenessAndTreeRelationships() throws Exception {
        var actor = admin(); long root = create(directory(null, "Root", ""), actor);
        long child = create(directory(root, "Child", ""), actor);
        long leaf = create(menu(child, "Products", "products", "/products", ""), actor);
        call(put(BASE + "/" + root).content(directory(child, "Cycle", ",\"version\":0")), actor, 400);
        call(put(BASE + "/" + root).content(directory(root, "Self", ",\"version\":0")), actor, 400);
        call(post(BASE).content(directory(leaf, "Invalid parent", "")), actor, 400);
        call(post(BASE).content(directory(Long.MAX_VALUE, "Missing parent", "")), actor, 400);
        call(post(BASE).content(menu(null, "Duplicate", "products", "/products", "")), actor, 409);
        for (String payload : List.of("{}", directory(null, " ", ""), menu(null, "Bad", "../pages/Admin", "/products", ""),
                menu(null, "Bad", "orders", "https://evil.test", ""), menu(null, "Bad", "orders", "/products", ""),
                directory(null, "Bad", ",\"path\":\"/products\""), directory(null, "Bad", ",\"sort\":-1"),
                directory(null, "Bad", ",\"icon\":\"<script>\""), directory(null, "Bad", ",\"requiredPermission\":\"missing:code\"")))
            call(post(BASE).content(payload), actor, 400);
        call(put(BASE + "/" + root).content(directory(null, "Updated", ",\"version\":0")), actor, 200);
        call(put(BASE + "/" + root).content(directory(null, "Stale", ",\"version\":0")), actor, 409);
        call(post(BASE + "/" + root + "/status").content(state("DISABLED", 0)), actor, 409);
        call(delete(BASE + "/" + root).param("version", "0"), actor, 409);
        call(get(BASE).param("pageSize", "101"), actor, 400);
        call(get(BASE).param("page", "0"), actor, 400);
        call(get(BASE).param("type", "PAGE"), actor, 400);
        call(get(BASE + "/" + Long.MAX_VALUE), actor, 404);
        call(delete(BASE + "/" + leaf).param("version", "0"), actor, 200);
        call(post(BASE).content(directory(leaf, "Deleted parent", "")), actor, 400);
    }

    @Test void exactAuthoritiesCsrfAndFreshDatabaseActorAreRequired() throws Exception {
        var actor = admin(); long root = create(directory(null, "Root", ""), actor);
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        for (String authority : List.of("system.manage", "system:menu:read", "system:permission:list")) {
            for (var request : List.of(get(BASE), get(BASE + "/tree"), post(BASE).content(directory(null, "No", "")),
                    put(BASE + "/" + root).content(directory(null, "No", ",\"version\":0")),
                    post(BASE + "/" + root + "/status").content(state("DISABLED", 0)), delete(BASE + "/" + root).param("version", "0")))
                mvc.perform(request.with(user("denied").authorities(() -> authority)).with(csrf()).contentType("application/json")).andExpect(status().isForbidden());
        }
        mvc.perform(post(BASE).with(user(actor)).contentType("application/json").content(directory(null, "CSRF", ""))).andExpect(status().isForbidden());
        call(post(BASE + "/1/grant"), actor, 403);
        jdbc.update("update sys_user set administrator=false where id=?", actor.getUser().userId());
        call(post(BASE).content(directory(null, "Stale admin", "")), actor, 403);
        assertThat(jdbc.queryForList("select code from sys_permission where code like 'system:menu:%' and deleted=false and status='ENABLED'", String.class))
                .containsExactlyInAnyOrder("system:menu:list", "system:menu:create", "system:menu:update", "system:menu:status", "system:menu:delete");
    }

    @Test void referencesAreTransactionalAndLiveRoleDependenciesBlockDelete() throws Exception {
        var actor = admin();
        long root = create(directory(null, "Root", ",\"requiredPermission\":\"system:menu:list\""), actor);
        long role = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Role') returning id", Long.class, "role" + suffix());
        long association = jdbc.queryForObject("insert into sys_role_menu(role_id,menu_id) values(?,?) returning id", Long.class, role, root);
        call(delete(BASE + "/" + root).param("version", "0"), actor, 400);
        jdbc.update("update sys_role set deleted=true where id=?", role);
        call(delete(BASE + "/" + root).param("version", "0"), actor, 200);
        assertThat(jdbc.queryForObject("select menu_id from sys_role_menu where id=?", Long.class, association)).isEqualTo(root);
        assertThat(jdbc.queryForObject("select deleted from sys_role_menu where id=?", Boolean.class, association)).isFalse();
    }

    @Test void managementTreeRetainsHiddenDisabledEntriesAndPrunesOnlyWhenRequested() throws Exception {
        var actor = admin(); long root = create(directory(null, "Hidden", ""), actor);
        long child = create(directory(root, "Child", ""), actor);
        jdbc.update("update sys_menu set visible=false,status='DISABLED' where id=?", root);
        var tree = call(get(BASE + "/tree"), actor, 200);
        assertThat(tree.get(0).path("id").asLong()).isEqualTo(root);
        assertThat(tree.get(0).path("children").get(0).path("id").asLong()).isEqualTo(child);
        assertThat(call(get(BASE + "/tree").param("visible", "true"), actor, 200)).isEmpty();
        assertThat(call(get(BASE + "/tree").param("status", "ENABLED"), actor, 200)).isEmpty();
        call(get(BASE + "/tree").param("status", "INVALID"), actor, 400);
    }

    SystemUserDetails admin() {
        String name = "menuadmin" + suffix();
        jdbc.update("insert into sys_user(username,display_name,status,administrator,must_change_password) values(?,'Fixture','ENABLED',true,false)", name);
        var principal = identities.loadPrincipal(name);
        return new SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
    }
    long create(String payload, SystemUserDetails actor) throws Exception { return call(post(BASE).content(payload), actor, 200).path("id").asLong(); }
    JsonNode call(MockHttpServletRequestBuilder request, SystemUserDetails actor, int expected) throws Exception {
        return json.readTree(mvc.perform(request.with(user(actor)).with(csrf()).contentType("application/json")).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString()).path("data");
    }
    static String directory(Long parent, String name, String extra) { return "{\"type\":\"DIRECTORY\",\"parentId\":" + parent + ",\"name\":\"" + name + "\",\"sort\":0,\"visible\":true,\"status\":\"ENABLED\"" + extra + "}"; }
    static String menu(Long parent, String name, String key, String path, String extra) { return directory(parent, name, ",\"routeKey\":\"" + key + "\",\"path\":\"" + path + "\"" + extra).replace("DIRECTORY", "MENU"); }
    static String state(String status, int version) { return "{\"status\":\"" + status + "\",\"version\":" + version + "}"; }
    static String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
