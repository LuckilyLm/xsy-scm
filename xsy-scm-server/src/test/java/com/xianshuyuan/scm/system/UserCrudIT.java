package com.xianshuyuan.scm.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserCrudIT extends IsolatedUserDatabase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Test void createsNormalizedNonAdministratorAndReturnsOnlySafeFields() throws Exception {
        String name = "Case" + suffix();
        JsonNode created = create(" " + name + " ");
        assertThat(created.path("username").asText()).isEqualTo(name.toLowerCase());
        assertThat(created.has("passwordHash")).isFalse();
        assertThat(created.has("authVersion")).isFalse();
        long id = created.path("id").asLong();
        assertThat(jdbc.queryForObject("select administrator from sys_user where id=?", Boolean.class, id)).isFalse();
        assertThat(encoder.matches("test-password-123", jdbc.queryForObject("select password_hash from sys_user where id=?", String.class,id))).isTrue();
        mvc.perform(get("/api/system/users/" + id).with(user("reader").authorities(() -> "system:user:list")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get("/api/system/users").param("keyword", name.toLowerCase()).with(user("reader").authorities(() -> "system:user:list")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test void rejectsDuplicateCaseInsensitiveUsername() throws Exception {
        String name = "unique" + suffix(); create(name);
        mvc.perform(post("/api/system/users").with(user("admin").authorities(() -> "system.administrator")).with(csrf())
            .contentType("application/json").content(payload(name.toUpperCase())))
            .andExpect(status().isConflict());
    }

    @Test void updatesProfileAndRejectsStaleVersionThenSoftDeletes() throws Exception {
        long id = create("edit" + suffix()).path("id").asLong();
        String update = "{\"displayName\":\"Changed\",\"email\":\"user@example.test\",\"version\":0}";
        mvc.perform(put("/api/system/users/"+id).with(user("editor").authorities(() -> "system:user:update")).with(csrf()).contentType("application/json").content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1)).andExpect(jsonPath("$.data.displayName").value("Changed"));
        mvc.perform(put("/api/system/users/"+id).with(user("editor").authorities(() -> "system:user:update")).with(csrf()).contentType("application/json").content(update)).andExpect(status().isConflict());
        mvc.perform(delete("/api/system/users/"+id).param("version","1").with(user("deleter").authorities(() -> "system:user:delete")).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select deleted from sys_user where id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,id)).isEqualTo(2L);
        mvc.perform(get("/api/system/users/"+id).with(user("reader").authorities(() -> "system:user:list"))).andExpect(status().isNotFound());
    }

    @Test void validatesDepartmentAndStateAndMissingUsers() throws Exception {
        long id = create("state"+suffix()).path("id").asLong();
        mvc.perform(put("/api/system/users/"+id).with(user("admin").authorities(() -> "system.administrator")).with(csrf()).contentType("application/json")
            .content("{\"displayName\":\"A\",\"departmentId\":9223372036854775807,\"version\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/system/users/"+id+"/status").with(user("admin").authorities(() -> "system:user:status")).with(csrf()).contentType("application/json")
            .content("{\"status\":\"DISABLED\",\"version\":0}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,id)).isEqualTo(1L);
        mvc.perform(post("/api/system/users/"+id+"/status").with(user("admin").authorities(() -> "system:user:status")).with(csrf()).contentType("application/json")
            .content("{\"status\":\"INVALID\",\"version\":1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/system/users/9223372036854775807").with(user("admin").authorities(() -> "system.administrator"))).andExpect(status().isNotFound());
    }

    @Test void rejectsUnauthorizedAndInvalidRequests() throws Exception {
        mvc.perform(get("/api/system/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/users").with(user("broad").authorities(() -> "system.manage"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/system/users").with(user("read").authorities(() -> "system:user:list")).with(csrf()).contentType("application/json").content(payload("denied"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/system/users").with(user("creator").authorities(() -> "system:user:create")).with(csrf()).contentType("application/json").content("{\"username\":\"test\",\"displayName\":\"Test\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/system/users").param("pageSize","10001").with(user("reader").authorities(() -> "system:user:list"))).andExpect(status().isBadRequest());
    }

    @Test void guardsAdministratorAndSelfFromDestructiveChanges() throws Exception {
        long id = create("protected"+suffix()).path("id").asLong();
        jdbc.update("update sys_user set administrator=true where id=?",id);
        mvc.perform(delete("/api/system/users/"+id).param("version","0").with(user("ordinary").authorities(() -> "system:user:delete")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/system/users/"+id+"/status").with(user("admin").authorities(() -> "system.administrator")).with(csrf()).contentType("application/json").content("{\"status\":\"DISABLED\",\"version\":0}")).andExpect(status().isForbidden());
        String name="self"+suffix(); long selfId=create(name).path("id").asLong();
        mvc.perform(delete("/api/system/users/"+selfId).param("version","0").with(user(name).authorities(() -> "system:user:delete")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void administratorCanRetireAnotherAdministratorWhenUsableSurvivorRemains() throws Exception {
        long target=create("retire"+suffix()).path("id").asLong();
        long survivor=create("survivor"+suffix()).path("id").asLong();
        jdbc.update("update sys_user set administrator=true where id in (?,?)",target,survivor);
        mvc.perform(delete("/api/system/users/"+target).param("version","0").with(user("operator").authorities(() -> "system.administrator")).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select deleted from sys_user where id=?",Boolean.class,target)).isTrue();
    }

    @Test void disabledLockedAndCredentiallessAdministratorsDoNotCountAsSurvivors() throws Exception {
        long target=create("last"+suffix()).path("id").asLong();
        long survivor=create("unusable"+suffix()).path("id").asLong();
        jdbc.update("update sys_user set administrator=true where id in (?,?)",target,survivor);
        for (String sql : java.util.List.of("status='DISABLED'", "status='ENABLED',locked_until=CURRENT_TIMESTAMP+interval '1 hour'", "locked_until=null,password_hash=null")) {
            jdbc.update("update sys_user set "+sql+" where id=?",survivor);
            mvc.perform(delete("/api/system/users/"+target).param("version","0").with(user("operator").authorities(() -> "system.administrator")).with(csrf())).andExpect(status().isForbidden());
        }
    }

    @Test void cannotEnableUserAssignedToDisabledDepartment() throws Exception {
        long department=jdbc.queryForObject("insert into sys_department(code,name,status) values(?,?,'DISABLED') returning id",Long.class,"dept"+suffix(),"Disabled");
        long id=create("deptuser"+suffix()).path("id").asLong();
        jdbc.update("update sys_user set department_id=?,status='DISABLED' where id=?",department,id);
        mvc.perform(post("/api/system/users/"+id+"/status").with(user("admin").authorities(() -> "system:user:status")).with(csrf()).contentType("application/json")
            .content("{\"status\":\"ENABLED\",\"version\":0}".replace("\"", "\""))).andExpect(status().isBadRequest());
    }

    @Test void invalidBcryptCostsCannotPreserveLastAdministrator() throws Exception {
        long target=create("costtarget"+suffix()).path("id").asLong();
        long survivor=create("costsurvivor"+suffix()).path("id").asLong();
        jdbc.update("update sys_user set administrator=true where id in (?,?)",target,survivor);
        String hash=encoder.encode("test-password-123");
        for (String cost : java.util.List.of("00","03","99")) {
            jdbc.update("update sys_user set password_hash=? where id=?",hash.substring(0,4)+cost+hash.substring(6),survivor);
            mvc.perform(delete("/api/system/users/"+target).param("version","0").with(user("operator").authorities(() -> "system.administrator")).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/api/system/users/"+target+"/status").with(user("operator").authorities(() -> "system.administrator")).with(csrf()).contentType("application/json").content("{\"status\":\"DISABLED\",\"version\":0}")).andExpect(status().isForbidden());
        }
    }

    @Test void everyEndpointDeniesUnrelatedPermissionsAndAllowsItsOwnPermission() throws Exception {
        long id=create("inventory"+suffix()).path("id").asLong();
        var requests=java.util.List.of(get("/api/system/users"),get("/api/system/users/"+id),
            post("/api/system/users").content(payload("allowed"+suffix())),
            put("/api/system/users/"+id).content("{\"displayName\":\"Changed\",\"version\":0}"),
            post("/api/system/users/"+id+"/status").content("{\"status\":\"DISABLED\",\"version\":1}"),
            delete("/api/system/users/"+id).param("version","2"));
        String[] permissions={"list","list","create","update","status","delete"};
        for(int i=0;i<requests.size();i++) {
            var request=requests.get(i).with(csrf()).contentType("application/json");
            for(String denied : java.util.List.of("system.manage","system:user:read","unrelated"))
                mvc.perform(request.with(user("denied").authorities(() -> denied))).andExpect(status().isForbidden());
            String allowed="system:user:"+permissions[i];
            mvc.perform(request.with(user("permitted").authorities(() -> allowed))).andExpect(status().isOk());
        }
    }

    private JsonNode create(String username) throws Exception {
        return json.readTree(mvc.perform(post("/api/system/users").with(user("admin").authorities(() -> "system.administrator")).with(csrf()).contentType("application/json").content(payload(username)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private String payload(String name) throws Exception { return json.writeValueAsString(Map.of("username",name,"displayName","Test User","password","test-password-123","administrator",true)); }
    private String suffix() { return UUID.randomUUID().toString().replace("-","").substring(0,12); }
}
