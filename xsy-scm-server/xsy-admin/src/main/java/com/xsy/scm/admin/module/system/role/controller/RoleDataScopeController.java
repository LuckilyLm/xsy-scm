package com.xsy.scm.admin.module.system.role.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.system.role.domain.form.RoleDataScopeUpdateForm;
import com.xsy.scm.admin.module.system.role.domain.vo.RoleDataScopeVO;
import com.xsy.scm.admin.module.system.role.service.RoleDataScopeService;
import com.xsy.scm.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色的数据权限配置
 *
 */
@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_ROLE_DATA_SCOPE)
public class RoleDataScopeController {

    @Resource
    private RoleDataScopeService roleDataScopeService;

    @Operation(summary = "获取某角色所设置的数据范围")
    @GetMapping("/role/dataScope/getRoleDataScopeList/{roleId}")
    public ResponseDTO<List<RoleDataScopeVO>> dataScopeListByRole(@PathVariable Long roleId) {
        return roleDataScopeService.getRoleDataScopeList(roleId);
    }

    @Operation(summary = "批量设置某角色数据范围")
    @PostMapping("/role/dataScope/updateRoleDataScopeList")
    @SaCheckPermission("system:role:dataScope:update")
    public ResponseDTO<String> updateRoleDataScopeList(
        @RequestBody @Valid RoleDataScopeUpdateForm roleDataScopeUpdateForm) {
        return roleDataScopeService.updateRoleDataScopeList(roleDataScopeUpdateForm);
    }

}
