package net.lab1024.sa.admin.module.scm.warehouse.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseScopeUpdateForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseScopeEmployeeVO;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseScopeWarehouseVO;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseScopeService;
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
 * 员工—仓库授权维护（SCM 数据范围的仓库维度）。
 *
 * <p>两个方向的读端点 + 一个整体替换的写端点。它服务的是「这个仓谁能看」这一件事，
 * 与仓库主数据本身（{@code /scm/warehouse/query} 等）是两个权限点：仓库管理员不该自动
 * 获得改可见范围的权力，反之负责授权的人也不必能动仓库主档。
 *
 * <p>读端点按仓库、按员工两个方向各查一次，故意不做统一分页列表：授权是「一人一仓」的
 * 稀疏关系，任何一张全量表格都会比它要回答的问题更难读。
 */
@RestController
@RequestMapping("/scm/warehouse/scope")
@Tag(name = "SCM 仓库授权")
@RequiredArgsConstructor
public class WarehouseScopeController {

    private final WarehouseScopeService service;

    /**
     * 某仓库下被授权的员工。
     */
    @GetMapping("/employees")
    @SaCheckPermission("scm:warehouse:scope:query")
    public ResponseDTO<List<WarehouseScopeEmployeeVO>> employees(@RequestParam Long warehouseId) {
        return ResponseDTO.ok(service.listEmployees(warehouseId));
    }

    /**
     * 某员工被授权的仓库。
     */
    @GetMapping("/warehouses")
    @SaCheckPermission("scm:warehouse:scope:query")
    public ResponseDTO<List<WarehouseScopeWarehouseVO>> warehouses(@RequestParam Long employeeId) {
        return ResponseDTO.ok(service.listWarehouses(employeeId));
    }

    /**
     * 整体替换某员工的活动授权；空清单即全部回收。
     */
    @PostMapping("/update")
    @SaCheckPermission("scm:warehouse:scope:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody WarehouseScopeUpdateForm form) {
        service.update(form);
        return ResponseDTO.ok();
    }
}
