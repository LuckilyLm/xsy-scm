package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.domain.form.InventoryWarningThresholdAddForm;
import com.xsy.scm.inventory.domain.form.InventoryWarningThresholdQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryWarningThresholdVO;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.inventory.service.InventoryWarningThresholdService;
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

    private final InventoryWarningThresholdService inventoryWarningThresholdService;

    private final InventoryWarningQueryService inventoryWarningQueryService;

    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.THRESHOLD_QUERY)
    public ResponseDTO<PageResult<InventoryWarningThresholdVO>> query(
            @Valid @RequestBody InventoryWarningThresholdQueryForm form) {
        return ResponseDTO.ok(inventoryWarningQueryService.queryThresholdPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(InventoryPermission.THRESHOLD_QUERY)
    public ResponseDTO<InventoryWarningThresholdVO> detail(@PathVariable("id") Long warningThresholdId) {
        return ResponseDTO.ok(inventoryWarningQueryService.detail(warningThresholdId));
    }

    /**
     * 新建阈值配置；同一 (仓库, SKU) 只允许一条。
     */
    @PostMapping("/create")
    @SaCheckPermission(InventoryPermission.THRESHOLD_ADD)
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody InventoryWarningThresholdAddForm form) {
        return ResponseDTO.ok(inventoryWarningThresholdService.create(form));
    }

    /**
     * 编辑阈值配置（可把某个边界清空 —— 传 null 即清空）。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission(InventoryPermission.THRESHOLD_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@PathVariable("id") Long warningThresholdId,
                                      @Valid @RequestBody InventoryWarningThresholdAddForm form) {
        inventoryWarningThresholdService.update(warningThresholdId, form);
        return ResponseDTO.ok();
    }

    /**
     * 删除阈值配置（逻辑删）。删除后该 (仓库, SKU) 不再产生预警。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission(InventoryPermission.THRESHOLD_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@PathVariable("id") Long warningThresholdId) {
        inventoryWarningThresholdService.delete(warningThresholdId);
        return ResponseDTO.ok();
    }
}
