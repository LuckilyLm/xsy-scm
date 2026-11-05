package com.xsy.scm.admin.module.system.menu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.system.menu.domain.form.MenuAddForm;
import com.xsy.scm.admin.module.system.menu.domain.form.MenuUpdateForm;
import com.xsy.scm.admin.module.system.menu.domain.vo.MenuTreeVO;
import com.xsy.scm.admin.module.system.menu.domain.vo.MenuVO;
import com.xsy.scm.admin.module.system.menu.service.MenuService;
import com.xsy.scm.base.common.domain.RequestUrlVO;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单
 *
 */
@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_MENU)
public class MenuController {

    @Resource
    private MenuService menuService;

    @Operation(summary = "添加菜单")
    @PostMapping("/menu/add")
    @SaCheckPermission("system:menu:add")
    public ResponseDTO<String> addMenu(@RequestBody @Valid MenuAddForm menuAddForm) {
        menuAddForm.setCreateUserId(XsyRequestUtil.getRequestUserId());
        return menuService.addMenu(menuAddForm);
    }

    @Operation(summary = "更新菜单")
    @PostMapping("/menu/update")
    @SaCheckPermission("system:menu:update")
    public ResponseDTO<String> updateMenu(@RequestBody @Valid MenuUpdateForm menuUpdateForm) {
        menuUpdateForm.setUpdateUserId(XsyRequestUtil.getRequestUserId());
        return menuService.updateMenu(menuUpdateForm);
    }

    @Operation(summary = "批量删除菜单")
    @GetMapping("/menu/batchDelete")
    @SaCheckPermission("system:menu:batchDelete")
    public ResponseDTO<String> batchDeleteMenu(@RequestParam("menuIdList") List<Long> menuIdList) {
        return menuService.batchDeleteMenu(menuIdList, XsyRequestUtil.getRequestUserId());
    }

    @Operation(summary = "查询菜单列表")
    @GetMapping("/menu/query")
    public ResponseDTO<List<MenuVO>> queryMenuList() {
        return ResponseDTO.ok(menuService.queryMenuList(null));
    }

    @Operation(summary = "查询菜单详情")
    @GetMapping("/menu/detail/{menuId}")
    public ResponseDTO<MenuVO> getMenuDetail(@PathVariable Long menuId) {
        return menuService.getMenuDetail(menuId);
    }

    @Operation(summary = "查询菜单树")
    @GetMapping("/menu/tree")
    public ResponseDTO<List<MenuTreeVO>> queryMenuTree(@RequestParam("onlyMenu") Boolean onlyMenu) {
        return menuService.queryMenuTree(onlyMenu);
    }

    @Operation(summary = "获取所有请求路径")
    @GetMapping("/menu/auth/url")
    public ResponseDTO<List<RequestUrlVO>> getAuthUrl() {
        return menuService.getAuthUrl();
    }
}
