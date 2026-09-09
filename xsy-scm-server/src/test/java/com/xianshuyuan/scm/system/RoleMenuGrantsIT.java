package com.xianshuyuan.scm.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleMenuGrantsIT extends IsolatedUserDatabase {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.xianshuyuan.scm.auth.service.AuthIdentityService identities;
    @Autowired com.xianshuyuan.scm.system.mapper.SystemUserMapper users;

    @Test void administratorReplacesExplicitGrantsWithoutAddingAncestors() throws Exception {
        String username = "rm" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into sys_user(username,display_name,administrator,must_change_password) values(?,'Menu grants',true,false)", username);
        var principal = identities.loadPrincipal(username);
        var actor = new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
        long role = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Menu grants') returning id", Long.class, username);
        long parent = jdbc.queryForObject("insert into sys_menu(type,name) values('DIRECTORY','Parent') returning id", Long.class);
        long child = jdbc.queryForObject("insert into sys_menu(type,name,parent_id,route_key,path) values('MENU','Products',?,'products','/products') returning id", Long.class, parent);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/system/roles/" + role + "/menus")
                .with(user(actor)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"menuIds\":[" + child + "],\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.menus.length()").value(1)).andExpect(jsonPath("$.data.menus[0].id").value(child));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList("select menu_id from sys_role_menu where role_id=? and deleted=false", Long.class, role)).containsExactly(child);
    }

    @Test void explicitDirectoryNeverGrantsChildrenOrApiAuthorities() throws Exception {
        var actor=actor(true); var target=actor(false); long role=role(), parent=menu(), child=menu();
        jdbc.update("update sys_menu set parent_id=?,required_permission_code='system:user:list' where id=?",parent,child);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",target.getUser().userId(),role);
        assign(role,java.util.List.of(parent),0,actor,200);
        org.assertj.core.api.Assertions.assertThat(service.read(role).menus()).extracting(com.xianshuyuan.scm.system.dto.RoleMenusResponse.Menu::id).containsExactly(parent);
        org.assertj.core.api.Assertions.assertThat(users.selectEnabledPermissionCodes(target.getUser().userId())).isEmpty();
    }
    @Test void administratorCanPrepareHiddenSelectionUnderDisabledHiddenAncestor() throws Exception {
        long role=role(), parent=menu(), selected=menu();
        jdbc.update("update sys_menu set visible=false,status='DISABLED' where id=?",parent);
        jdbc.update("update sys_menu set parent_id=?,visible=false,required_permission_code='system:user:list' where id=?",parent,selected);
        assign(role,java.util.List.of(selected),0,actor(true),200);
        org.assertj.core.api.Assertions.assertThat(service.read(role).menus()).hasSize(1);
    }
    @Test void mixedReplacementPreservesRetainedRelationshipIdentity() throws Exception {
        var actor=actor(true); long role=role(), retained=menu(), removed=menu(), added=menu();
        assign(role,java.util.List.of(retained,removed),0,actor,200);
        long relation=jdbc.queryForObject("select id from sys_role_menu where role_id=? and menu_id=? and deleted=false",Long.class,role,retained);
        assign(role,java.util.List.of(retained,added),1,actor,200);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select id from sys_role_menu where role_id=? and menu_id=? and deleted=false",Long.class,role,retained)).isEqualTo(relation);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList("select menu_id from sys_role_menu where role_id=? and deleted=false",Long.class,role)).containsExactlyInAnyOrder(retained,added);
    }
    @Test void staleVersionCannotPerformNoop() throws Exception {
        long role=role(); assign(role,java.util.List.of(),1,actor(true),409);
        org.assertj.core.api.Assertions.assertThat(audits(role)).isZero();
    }
    @Test void emptyNoopLeavesVersionAndAuditUntouched() throws Exception {
        var actor = actor(true); long role = role();
        assign(role, java.util.List.of(), 0, actor, 200);
        org.assertj.core.api.Assertions.assertThat(version(role)).isZero();
        org.assertj.core.api.Assertions.assertThat(audits(role)).isZero();
    }

    @Test void nonexistentMenuIsRejectedWithoutMutatingRole() throws Exception {
        long role = role();
        assign(role, java.util.List.of(Long.MAX_VALUE), 0, actor(true), 404);
        org.assertj.core.api.Assertions.assertThat(version(role)).isZero();
    }

    @Test void clearingSoftDeletesAndReaddingCreatesNewHistory() throws Exception {
        var actor=actor(true); long role=role(), menu=menu();
        long original=jdbc.queryForObject("insert into sys_role_menu(role_id,menu_id) values(?,?) returning id",Long.class,role,menu);
        assign(role,java.util.List.of(),0,actor,200);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select deleted from sys_role_menu where id=?",Boolean.class,original)).isTrue();
        assign(role,java.util.List.of(menu),1,actor,200);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select id from sys_role_menu where role_id=? and deleted=false",Long.class,role)).isNotEqualTo(original);
    }
    @Test void disabledMenuIsRejected() throws Exception {
        long role=role(), menu=menu(); jdbc.update("update sys_menu set status='DISABLED' where id=?",menu);
        assign(role,java.util.List.of(menu),0,actor(true),400);
    }
    @Test void duplicateNoopIsRejected() throws Exception {
        long role=role(), menu=menu(); jdbc.update("insert into sys_role_menu(role_id,menu_id) values(?,?)",role,menu);
        assign(role,java.util.List.of(menu,menu),0,actor(true),400);
    }
    @Test void mutationAuditsAndInvalidatesUsersOfDisabledRole() throws Exception {
        var actor=actor(true); var target=actor(false); long role=role(),menu=menu();
        jdbc.update("update sys_role set status='DISABLED' where id=?",role);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",target.getUser().userId(),role);
        assign(role,java.util.List.of(menu),0,actor,200);
        org.assertj.core.api.Assertions.assertThat(audits(role)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select auth_version from sys_user where id=?",Long.class,target.getUser().userId())).isEqualTo(1);
    }
    @Test void lockedDatabaseActorCannotPerformNoop() throws Exception {
        var actor=actor(true); long role=role();
        jdbc.update("update sys_user set locked_until=CURRENT_TIMESTAMP + interval '1 hour' where id=?",actor.getUser().userId());
        assign(role,java.util.List.of(),0,actor,403);
    }
    @Test void delegatedActorCanAssignHeldNavigationToDisabledRole() throws Exception {
        var original=actor(false); long own=role(), target=role(), menu=menu();
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",original.getUser().userId(),own);
        jdbc.update("insert into sys_role_menu(role_id,menu_id) values(?,?)",own,menu);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:assign-menus' and deleted=false",own);
        jdbc.update("update sys_role set status='DISABLED' where id=?",target);
        users.lockSecurityWrites();
        var principal=identities.loadPrincipal(original.getUsername());
        var delegated=new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal,null,identities.loadAuthorities(principal),true,true);
        assign(target,java.util.List.of(menu),0,delegated,200);
    }
    @Test void delegatedActorCannotGrantUnheldOrHiddenNavigationOrManageReservedRole() throws Exception {
        var original=actor(false); long own=role(), target=role(), held=menu(), unheld=menu();
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",original.getUser().userId(),own);
        jdbc.update("insert into sys_role_menu(role_id,menu_id) values(?,?)",own,held);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:assign-menus' and deleted=false",own);
        users.lockSecurityWrites();
        var p=identities.loadPrincipal(original.getUsername());
        var delegated=new com.xianshuyuan.scm.auth.security.SystemUserDetails(p,null,identities.loadAuthorities(p),true,true);
        assign(target,java.util.List.of(unheld),0,delegated,403);
        jdbc.update("update sys_menu set visible=false where id=?",held);
        assign(target,java.util.List.of(held),0,delegated,403);
        jdbc.update("update sys_role set system_role=true where id=?",target);
        assign(target,java.util.List.of(),0,delegated,403);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"permission", "role", "association"})
    void currentDatabaseRevocationRejectsDelegatedNoop(String revoked) {
        var original=actor(false); long own=role(), target=role();
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",original.getUser().userId(),own);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:assign-menus' and deleted=false",own);
        users.lockSecurityWrites(); var p=identities.loadPrincipal(original.getUsername());
        var delegated=new com.xianshuyuan.scm.auth.security.SystemUserDetails(p,null,identities.loadAuthorities(p),true,true);
        switch(revoked) {
            case "permission" -> jdbc.update("update sys_role_permission set deleted=true where role_id=?",own);
            case "role" -> jdbc.update("update sys_role set status='DISABLED' where id=?",own);
            case "association" -> jdbc.update("update sys_user_role set deleted=true where role_id=?",own);
        }
        var authentication=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(delegated,null,delegated.getAuthorities());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.replace(target,new com.xianshuyuan.scm.system.dto.ReplaceRoleMenusRequest(java.util.List.of(),0),authentication)).isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
        org.assertj.core.api.Assertions.assertThat(version(target)).isZero();
    }
    @Test void disabledDatabaseActorCannotPerformNoop() throws Exception {
        var actor=actor(true); long role=role();
        jdbc.update("update sys_user set status='DISABLED' where id=?",actor.getUser().userId());
        var authentication=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.getAuthorities());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.replace(role,new com.xianshuyuan.scm.system.dto.ReplaceRoleMenusRequest(java.util.List.of(),0),authentication))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
    }
    @Test void staleDatabaseActorCannotPerformNoop() throws Exception {
        var actor=actor(true); long role=role();
        jdbc.update("update sys_user set auth_version=auth_version+1 where id=?",actor.getUser().userId());
        var authentication=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.getAuthorities());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.replace(role,new com.xianshuyuan.scm.system.dto.ReplaceRoleMenusRequest(java.util.List.of(),0),authentication))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"hidden", "disabled", "deleted", "gate", "cycle", "menu-parent", "selected-gate"})
    void delegatedActorCannotAssignIneffectiveNavigation(String obstruction) throws Exception {
        var original=actor(false); long own=role(), target=role(), parent=menu(), selected=menu();
        jdbc.update("update sys_menu set parent_id=? where id=?",parent,selected);
        switch (obstruction) {
            case "hidden" -> jdbc.update("update sys_menu set visible=false where id=?",parent);
            case "disabled" -> jdbc.update("update sys_menu set status='DISABLED' where id=?",parent);
            case "deleted" -> jdbc.update("update sys_menu set deleted=true where id=?",parent);
            case "gate" -> jdbc.update("update sys_menu set required_permission_code='system:user:list' where id=?",parent);
            case "selected-gate" -> jdbc.update("update sys_menu set required_permission_code='system:user:list' where id=?",selected);
            case "cycle" -> jdbc.update("update sys_menu set parent_id=? where id=?",selected,parent);
            case "menu-parent" -> jdbc.update("update sys_menu set type='MENU',route_key='products',path='/products' where id=?",parent);
        }
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",original.getUser().userId(),own);
        jdbc.update("insert into sys_role_menu(role_id,menu_id) values(?,?)",own,selected);
        jdbc.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where code='system:role:assign-menus' and deleted=false",own);
        users.lockSecurityWrites();
        var p=identities.loadPrincipal(original.getUsername());
        var delegated=new com.xianshuyuan.scm.auth.security.SystemUserDetails(p,null,identities.loadAuthorities(p),true,true);
        assign(target,java.util.List.of(selected),0,delegated,java.util.Set.of("deleted","cycle","menu-parent").contains(obstruction) ? 400 : 403);
        org.assertj.core.api.Assertions.assertThat(version(target)).isZero();
        org.assertj.core.api.Assertions.assertThat(audits(target)).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missing", "deleted", "cycle", "menu-parent"})
    void administratorCannotAssignStructurallyInvalidNavigation(String obstruction) throws Exception {
        long target=role(), parent=menu(), selected=menu();
        jdbc.update("update sys_menu set parent_id=? where id=?",parent,selected);
        switch (obstruction) {
            case "missing" -> jdbc.update("update sys_menu set parent_id=? where id=?",Long.MAX_VALUE,selected);
            case "deleted" -> jdbc.update("update sys_menu set deleted=true where id=?",parent);
            case "cycle" -> jdbc.update("update sys_menu set parent_id=? where id=?",selected,parent);
            case "menu-parent" -> jdbc.update("update sys_menu set type='MENU',route_key='products',path='/products' where id=?",parent);
        }
        assign(target,java.util.List.of(selected),0,actor(true),400);
        org.assertj.core.api.Assertions.assertThat(version(target)).isZero();
    }
    @Test void unauthenticatedPrincipalCannotPerformNoop() {
        var actor=actor(true); long role=role();
        var authentication=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.unauthenticated(actor,null);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.replace(role,new com.xianshuyuan.scm.system.dto.ReplaceRoleMenusRequest(java.util.List.of(),0),authentication))
                .isInstanceOf(com.xianshuyuan.scm.common.exception.BusinessException.class);
    }
    @Autowired com.xianshuyuan.scm.system.service.RoleMenuGrantService service;
    private long menu() { return jdbc.queryForObject("insert into sys_menu(type,name) values('DIRECTORY','Menu grants') returning id",Long.class); }

    private com.xianshuyuan.scm.auth.security.SystemUserDetails actor(boolean admin) {
        String username = "rm" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into sys_user(username,display_name,administrator,must_change_password) values(?,'Menu grants',?,false)", username, admin);
        var principal = identities.loadPrincipal(username);
        return new com.xianshuyuan.scm.auth.security.SystemUserDetails(principal, null, identities.loadAuthorities(principal), true, true);
    }
    private long role() { return jdbc.queryForObject("insert into sys_role(code,name) values(?,'Menu grants') returning id", Long.class, "rm" + UUID.randomUUID().toString().replace("-", "")); }
    private int version(long role) { return jdbc.queryForObject("select version from sys_role where id=?", Integer.class, role); }
    private long audits(long role) { return jdbc.queryForObject("select count(*) from sys_operation_log where target_type='ROLE' and operation_code='ROLE_ASSIGN_MENUS' and target_id=?", Long.class, Long.toString(role)); }
    private void assign(long role, java.util.List<Long> ids, int version, com.xianshuyuan.scm.auth.security.SystemUserDetails actor, int expected) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/system/roles/" + role + "/menus")
                .with(user(actor)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"menuIds\":" + ids + ",\"version\":" + version + "}"))
                .andExpect(status().is(expected));
    }

    @Test void exactAuthorityCsrfAndRequestValidationAreEnforced() throws Exception {
        long role=role(); String path="/api/system/roles/"+role+"/menus";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(String denied:java.util.List.of("system.manage","system:role:assign-permissions","system:menu:list")) {
            mvc.perform(get(path).with(user("denied").authorities(()->denied))).andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(user("denied").authorities(()->denied)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"menuIds\":[],\"version\":0}")).andExpect(status().isForbidden());
        }
        var actor=actor(true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(user(actor)).contentType("application/json").content("{\"menuIds\":[],\"version\":0}")).andExpect(status().isForbidden());
        for(String body:java.util.List.of("{}","{\"menuIds\":null,\"version\":0}","{\"menuIds\":[null],\"version\":0}","{\"menuIds\":[-1],\"version\":0}","{\"menuIds\":[],\"version\":-1}","{\"menuIds\":"+java.util.Collections.nCopies(101,1)+",\"version\":0}"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(user(actor)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path).with(user(actor)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(version(role)).isZero();
    }

    @Test void roleReaderCanReadExplicitMenuAssignments() throws Exception {
        long role = jdbc.queryForObject("insert into sys_role(code,name) values(?,'Menu grants') returning id", Long.class, "rm" + UUID.randomUUID().toString().replace("-", ""));
        mvc.perform(get("/api/system/roles/" + role + "/menus").with(user("reader").authorities(() -> "system:role:list")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roleId").value(role))
                .andExpect(jsonPath("$.data.version").value(0)).andExpect(jsonPath("$.data.menus").isEmpty());
    }
}
