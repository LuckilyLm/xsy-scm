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
class DepartmentCrudIT extends IsolatedUserDatabase {
    private static final String BASE="/api/system/departments";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test void createsListsDetailsAndBuildsSortedTreeAndDescendants() throws Exception {
        long root=create(null,5), second=create(root,9), first=create(root,1), leaf=create(first,0);
        call(get(BASE+"/"+root),"list",200);
        var page=call(get(BASE).param("parentId",Long.toString(root)),"list",200);
        assertThat(page.path("total").asInt()).isEqualTo(2);
        assertThat(page.path("records").get(0).path("id").asLong()).isEqualTo(first);
        var tree=call(get(BASE+"/tree"),"list",200);
        var node=java.util.stream.StreamSupport.stream(tree.spliterator(),false).filter(n->n.path("id").asLong()==root).findFirst().orElseThrow();
        assertThat(node.path("children").get(0).path("id").asLong()).isEqualTo(first);
        assertThat(node.path("children").get(1).path("id").asLong()).isEqualTo(second);
        assertThat(node.path("children").get(0).path("children").get(0).path("id").asLong()).isEqualTo(leaf);
        var descendants=call(get(BASE+"/"+root+"/descendants"),"list",200);
        assertThat(java.util.stream.StreamSupport.stream(descendants.spliterator(),false).map(n->n.path("id").asLong()).toList())
            .containsExactlyInAnyOrder(first,second,leaf);
    }

    @Test void movesRetainedTreeRejectsSelfAndDescendantCycles() throws Exception {
        long root=create(null,0), child=create(root,0), leaf=create(child,0), other=create(null,0);
        call(put(BASE+"/"+root).content(update(root,0)),"update",400);
        call(put(BASE+"/"+root).content(update(leaf,0)),"update",400);
        call(put(BASE+"/"+child).content(update(other,0)),"update",200);
        assertThat(jdbc.queryForObject("select parent_id from sys_department where id=?",Long.class,child)).isEqualTo(other);
        assertThat(call(get(BASE+"/"+root+"/descendants"),"list",200)).isEmpty();
        assertThat(call(get(BASE+"/"+other+"/descendants"),"list",200).size()).isEqualTo(2);
        call(put(BASE+"/"+child).content(update(null,1)),"update",200);
        assertThat(jdbc.queryForObject("select parent_id from sys_department where id=?",Long.class,child)).isNull();
    }

    @Test void rejectsMissingDeletedDisabledParentsForCreateMoveAndEnable() throws Exception {
        long target=create(null,0), disabled=create(null,0), deleted=create(null,0);
        call(post(BASE+"/"+disabled+"/status").content(state("DISABLED",0)),"status",200);
        call(delete(BASE+"/"+deleted).param("version","0"),"delete",200);
        for(long parent:List.of(Long.MAX_VALUE,disabled,deleted)) {
            call(post(BASE).content(payload(parent,0)),"create",400);
            call(put(BASE+"/"+target).content(update(parent,0)),"update",400);
        }
        // Imported legacy data must not permit enabling a child under a disabled parent.
        jdbc.update("update sys_department set parent_id=?,status='DISABLED' where id=?",disabled,target);
        call(post(BASE+"/"+target+"/status").content(state("ENABLED",0)),"status",400);
    }

    @Test void disableAndDeleteRejectNondeletedChildrenEvenWhenDisabled() throws Exception {
        long parent=create(null,0), child=create(parent,0);
        for(String state:List.of("ENABLED","DISABLED")) {
            jdbc.update("update sys_department set status=? where id=?",state,child);
            call(post(BASE+"/"+parent+"/status").content(state("DISABLED",0)),"status",400);
            call(delete(BASE+"/"+parent).param("version","0"),"delete",400);
        }
        call(delete(BASE+"/"+child).param("version","0"),"delete",200);
        call(post(BASE+"/"+parent+"/status").content(state("DISABLED",0)),"status",200);
        call(post(BASE+"/"+parent+"/status").content(state("ENABLED",1)),"status",200);
        call(delete(BASE+"/"+parent).param("version","2"),"delete",200);
    }

    @Test void disabledAssignedUsersBlockDisableAndDeleteUntilSoftDeleted() throws Exception {
        long department=create(null,0);
        long user=jdbc.queryForObject("insert into sys_user(username,display_name,department_id,status) values(?,?,?,'ENABLED') returning id",Long.class,"assigned"+suffix(),"Assigned",department);
        for(String state:List.of("ENABLED","DISABLED")) {
            jdbc.update("update sys_user set status=? where id=?",state,user);
            call(post(BASE+"/"+department+"/status").content(state("DISABLED",0)),"status",400);
            call(delete(BASE+"/"+department).param("version","0"),"delete",400);
        }
        assertThat(jdbc.queryForObject("select version from sys_department where id=?",Integer.class,department)).isZero();
        jdbc.update("update sys_user set deleted=true where id=?",user);
        call(post(BASE+"/"+department+"/status").content(state("DISABLED",0)),"status",200);
        call(delete(BASE+"/"+department).param("version","1"),"delete",200);
    }

    @Test void rejectsStaleVersionsMissingResourcesAndRetainsSoftDeletedRows() throws Exception {
        long id=create(null,0);
        call(put(BASE+"/"+id).content(update(null,0)),"update",200);
        call(put(BASE+"/"+id).content(update(null,0)),"update",409);
        call(post(BASE+"/"+id+"/status").content(state("DISABLED",0)),"status",409);
        call(delete(BASE+"/"+id).param("version","0"),"delete",409);
        call(delete(BASE+"/"+id).param("version","1"),"delete",200);
        assertThat(jdbc.queryForObject("select deleted from sys_department where id=?",Boolean.class,id)).isTrue();
        for(long missing:List.of(id,Long.MAX_VALUE)) {
            call(get(BASE+"/"+missing),"list",404);
            call(get(BASE+"/"+missing+"/descendants"),"list",404);
            call(put(BASE+"/"+missing).content(update(null,0)),"update",404);
            call(post(BASE+"/"+missing+"/status").content(state("DISABLED",0)),"status",404);
            call(delete(BASE+"/"+missing).param("version","0"),"delete",404);
        }
    }

    @Test void validatesActualControllerInputsAndCodeUniqueness() throws Exception {
        String payload=payload(null,0);
        call(post(BASE).content(payload),"create",200);
        call(post(BASE).content(payload),"create",409);
        for(String invalid:List.of("{}","{\"code\":\"A\",\"name\":\" \"}","{\"code\":\"A\",\"name\":\"A\",\"sortOrder\":-1}","{\"code\":\"A\",\"name\":\"A\",\"parentId\":0,\"sortOrder\":0}"))
            call(post(BASE).content(invalid),"create",400);
        long id=create(null,0);
        call(put(BASE+"/"+id).content("{\"name\":\"A\",\"sortOrder\":0}"),"update",400);
        call(post(BASE+"/"+id+"/status").content(state("INVALID",0)),"status",400);
        call(post(BASE+"/"+id+"/status").content(state("ENABLED",-1)),"status",400);
        call(delete(BASE+"/"+id).param("version","-1"),"delete",400);
        call(get(BASE+"/0"),"list",400);
        call(get(BASE).param("pageSize","101"),"list",400);
        call(get(BASE).param("status","INVALID"),"list",400);
    }

    @Test void allPermissionRoutesDenyBroadAuthorityAndAllowExactColonPermission() throws Exception {
        long id=create(null,0);
        var requests=List.of(get(BASE),get(BASE+"/tree"),get(BASE+"/"+id),get(BASE+"/"+id+"/descendants"),
            post(BASE).content(payload(null,0)),put(BASE+"/"+id).content(update(null,0)),
            post(BASE+"/"+id+"/status").content(state("DISABLED",1)),delete(BASE+"/"+id).param("version","2"));
        String[] allowed={"list","list","list","list","create","update","status","delete"};
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        for(int i=0;i<requests.size();i++) {
            for(String denied:List.of("system.manage","system:user:list","system:department:read"))
                mvc.perform(requests.get(i).with(user("denied").authorities(()->denied)).with(csrf()).contentType("application/json")).andExpect(status().isForbidden());
            call(requests.get(i),allowed[i],200);
        }
        mvc.perform(post(BASE+"/1/unknown").with(user("admin").authorities(()->"system.administrator")).with(csrf())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("select code from sys_permission where code like 'system:department:%' and deleted=false and status='ENABLED'",String.class))
            .containsExactlyInAnyOrder("system:department:list","system:department:create","system:department:update","system:department:status","system:department:delete");
    }

    @Test void auditsImmutableActorAndDoesNotChangeUnchangedAuthorizationIdentityOnMove() throws Exception {
        long id=create(null,0), parent=create(null,0);
        long assigned=jdbc.queryForObject("insert into sys_user(username,display_name,department_id,status) values(?,?,?,'ENABLED') returning id",Long.class,"identity"+suffix(),"Identity",id);
        var principal=new com.xianshuyuan.scm.auth.security.AuthenticatedUser(800001L,"actor","Actor",0,false,false,List.of());
        var details=new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal,null,List.of(()->"system:department:update"),true,true);
        // Use a real immutable principal without AccountVersionFilter rejecting the synthetic fixture.
        jdbc.update("insert into sys_user(id,username,display_name,status,must_change_password) values(800001,'actor','Actor','ENABLED',false)");
        mvc.perform(put(BASE+"/"+id).with(user(details)).with(csrf()).contentType("application/json").content(update(parent,0))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select actor_user_id from sys_operation_log where target_type='DEPARTMENT' and target_id=? and operation_code='DEPARTMENT_UPDATE'",Long.class,Long.toString(id))).isEqualTo(800001L);
        assertThat(jdbc.queryForObject("select before_data->>'parentId' from sys_operation_log where target_type='DEPARTMENT' and target_id=? and operation_code='DEPARTMENT_UPDATE'",String.class,Long.toString(id))).isNull();
        assertThat(jdbc.queryForObject("select after_data->>'parentId' from sys_operation_log where target_type='DEPARTMENT' and target_id=? and operation_code='DEPARTMENT_UPDATE'",String.class,Long.toString(id))).isEqualTo(Long.toString(parent));
        assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,assigned)).isZero();
        assertThat(jdbc.queryForObject("select department_id from sys_user where id=?",Long.class,assigned)).isEqualTo(id);
    }

    private long create(Long parent,int sort) throws Exception {
        return json.readTree(mvc.perform(post(BASE).with(user("admin").authorities(()->"system.administrator")).with(csrf()).contentType("application/json").content(payload(parent,sort)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
    }
    private JsonNode call(MockHttpServletRequestBuilder request,String permission,int expected) throws Exception {
        return json.readTree(mvc.perform(request.with(user("operator").authorities(()->"system:department:"+permission)).with(csrf()).contentType("application/json"))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }
    private String payload(Long parent,int sort) { return "{\"code\":\"dept"+suffix()+"\",\"name\":\"Department\",\"parentId\":"+parent+",\"sortOrder\":"+sort+"}"; }
    private String update(Long parent,int version) { return "{\"code\":\"edit"+suffix()+"\",\"name\":\"Changed\",\"parentId\":"+parent+",\"sortOrder\":2,\"version\":"+version+"}"; }
    private String state(String state,int version) { return "{\"status\":\""+state+"\",\"version\":"+version+"}"; }
    private String suffix() { return UUID.randomUUID().toString().replace("-","").substring(0,12); }
}
