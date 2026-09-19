package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningThresholdVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningThresholdService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 库存预警阈值配置。
 *
 * <p>共 5 个端点：分页 / 详情 / 新建 / 编辑 / 删除。
 *
 * <p><b>本模块不改变库存</b>：阈值是配置，余额是派生状态，两者的写路径完全分开 ——
 * 配置路径不会、也不应该创建余额行（否则就会造出「没有任何流水支撑的余额行」）。
 * 因此这里没有「确认 / 审批」这类动作，改配置立即生效（预警是读时计算的，天然实时）。
 *
 * <p>权限 {@code scm:inventory:threshold:query|add|update|delete}。
 */
@RestController
@RequestMapping("/scm/inventory/threshold")
@Tag(name = "SCM 库存预警阈值")
@RequiredArgsConstructor
public class InventoryWarningThresholdController {

    private final InventoryWarningThresholdService service;

    private final InventoryWarningQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:threshold:query")
    public ResponseDTO<PageResult<InventoryWarningThresholdVO>> query(
            @Valid @RequestBody InventoryWarningThresholdQueryForm form) {
        return ResponseDTO.ok(queryService.queryThresholdPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:inventory:threshold:query")
    public ResponseDTO<InventoryWarningThresholdVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queryService.detail(id));
    }

    /** 新建阈值配置；同一 (仓库, SKU) 只允许一条。 */
    @PostMapping("/create")
    @SaCheckPermission("scm:inventory:threshold:add")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody InventoryWarningThresholdAddForm form) {
        return ResponseDTO.ok(service.create(form));
    }

    /** 编辑阈值配置（可把某个边界清空 —— 传 null 即清空）。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("scm:inventory:threshold:update")
    @OperateLog
    public ResponseDTO<String> update(@PathVariable Long id,
                                      @Valid @RequestBody InventoryWarningThresholdAddForm form) {
        service.update(id, form);
        return ResponseDTO.ok();
    }

    /** 删除阈值配置（逻辑删）。删除后该 (仓库, SKU) 不再产生预警。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("scm:inventory:threshold:delete")
    @OperateLog
    public ResponseDTO<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseDTO.ok();
    }
}
