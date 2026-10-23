package com.xsy.scm.warehouse.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.warehouse.domain.form.WarehouseScopeUpdateForm;
import com.xsy.scm.warehouse.domain.vo.WarehouseScopeEmployeeVO;
import com.xsy.scm.warehouse.domain.vo.WarehouseScopeWarehouseVO;
import com.xsy.scm.warehouse.permission.WarehousePermission;
import com.xsy.scm.warehouse.service.WarehouseScopeService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仓库权限分配与仓库资料维护使用不同权限，管理员不能因为能编辑仓库就自动修改员工授权。
 */
@RestController
@RequestMapping("/scm/warehouse/scope")
@Tag(name = "SCM 仓库授权")
@RequiredArgsConstructor
public class WarehouseScopeController {

    private final WarehouseScopeService warehouseScopeService;

    /**
     * 某仓库下被授权的员工。
     */
    @GetMapping("/employees")
    @SaCheckPermission(WarehousePermission.SCOPE_QUERY)
    public ResponseDTO<List<WarehouseScopeEmployeeVO>> employees(@RequestParam Long warehouseId) {
        return ResponseDTO.ok(warehouseScopeService.listEmployees(warehouseId));
    }

    /**
     * 某员工被授权的仓库。
     */
    @GetMapping("/warehouses")
    @SaCheckPermission(WarehousePermission.SCOPE_QUERY)
    public ResponseDTO<List<WarehouseScopeWarehouseVO>> warehouses(@RequestParam Long employeeId) {
        return ResponseDTO.ok(warehouseScopeService.listWarehouses(employeeId));
    }

    /**
     * 整体替换某员工的活动授权；空清单即全部回收。
     */
    @PostMapping("/update")
    @SaCheckPermission(WarehousePermission.SCOPE_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody WarehouseScopeUpdateForm form) {
        warehouseScopeService.update(form);
        return ResponseDTO.ok();
    }
}
