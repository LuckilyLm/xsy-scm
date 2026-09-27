package com.xsy.scm.warehouse.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import com.xsy.scm.warehouse.domain.form.WarehouseQueryForm;
import com.xsy.scm.warehouse.domain.form.WarehouseStatusForm;
import com.xsy.scm.warehouse.domain.form.WarehouseUpdateForm;
import com.xsy.scm.warehouse.domain.vo.WarehouseVO;
import com.xsy.scm.warehouse.permission.WarehousePermission;
import com.xsy.scm.warehouse.service.WarehouseQueryService;
import com.xsy.scm.warehouse.service.WarehouseService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCM 仓库（最小主数据，）。
 *
 * <p>共 7 个端点。没有删除端点：仓库是主数据，{@code status} 通过独立命令表达启停。
 */
@RestController
@RequestMapping("/scm/warehouse")
@Tag(name = "SCM 仓库")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    private final WarehouseQueryService warehouseQueryService;

    /**
     * 下拉选择器：只返回 ENABLED 仓库。
     */
    @GetMapping("/list")
    @SaCheckPermission(WarehousePermission.QUERY)
    public ResponseDTO<List<WarehouseVO>> list() {
        return ResponseDTO.ok(warehouseQueryService.list());
    }

    @PostMapping("/query")
    @SaCheckPermission(WarehousePermission.QUERY)
    public ResponseDTO<PageResult<WarehouseVO>> query(@Valid @RequestBody WarehouseQueryForm form) {
        return ResponseDTO.ok(warehouseQueryService.query(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(WarehousePermission.QUERY)
    public ResponseDTO<WarehouseVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(warehouseQueryService.detail(id));
    }

    @PostMapping("/create")
    @SaCheckPermission(WarehousePermission.ADD)
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody WarehouseAddForm form) {
        return ResponseDTO.ok(warehouseService.create(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(WarehousePermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody WarehouseUpdateForm form) {
        warehouseService.update(form);
        return ResponseDTO.ok();
    }

    /**
     * 启用仓库。
     */
    @PostMapping("/enable")
    @SaCheckPermission(WarehousePermission.ENABLE)
    @OperateLog
    public ResponseDTO<String> enable(@Valid @RequestBody WarehouseStatusForm form) {
        warehouseService.enable(form);
        return ResponseDTO.ok();
    }

    /**
     * 停用仓库。
     */
    @PostMapping("/disable")
    @SaCheckPermission(WarehousePermission.DISABLE)
    @OperateLog
    public ResponseDTO<String> disable(@Valid @RequestBody WarehouseStatusForm form) {
        warehouseService.disable(form);
        return ResponseDTO.ok();
    }
}
