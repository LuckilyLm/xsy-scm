package net.lab1024.sa.admin.module.scm.warehouse.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseQueryForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseStatusForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseUpdateForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseVO;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseQueryService;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
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
 * SCM 仓库（最小主数据，W5 Target Design §7.1）。
 *
 * <p>共 7 个端点。没有删除端点：仓库是主数据，{@code status} 通过独立命令表达启停。
 */
@RestController
@RequestMapping("/scm/warehouse")
@Tag(name = "SCM 仓库")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService service;

    private final WarehouseQueryService queryService;

    /** 下拉选择器：只返回 ENABLED 仓库。 */
    @GetMapping("/list")
    @SaCheckPermission("scm:warehouse:query")
    public ResponseDTO<List<WarehouseVO>> list() {
        return ResponseDTO.ok(queryService.list());
    }

    @PostMapping("/query")
    @SaCheckPermission("scm:warehouse:query")
    public ResponseDTO<PageResult<WarehouseVO>> query(@Valid @RequestBody WarehouseQueryForm form) {
        return ResponseDTO.ok(queryService.query(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:warehouse:query")
    public ResponseDTO<WarehouseVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queryService.detail(id));
    }

    @PostMapping("/create")
    @SaCheckPermission("scm:warehouse:add")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody WarehouseAddForm form) {
        return ResponseDTO.ok(service.create(form));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:warehouse:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody WarehouseUpdateForm form) {
        service.update(form);
        return ResponseDTO.ok();
    }

    /** 启用仓库（B1，HD-B1-01）。 */
    @PostMapping("/enable")
    @SaCheckPermission("scm:warehouse:enable")
    @OperateLog
    public ResponseDTO<String> enable(@Valid @RequestBody WarehouseStatusForm form) {
        service.enable(form);
        return ResponseDTO.ok();
    }

    /** 停用仓库（B1，HD-B1-01 严格模式）。 */
    @PostMapping("/disable")
    @SaCheckPermission("scm:warehouse:disable")
    @OperateLog
    public ResponseDTO<String> disable(@Valid @RequestBody WarehouseStatusForm form) {
        service.disable(form);
        return ResponseDTO.ok();
    }
}
