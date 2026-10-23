package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.domain.form.InventoryConversionAddForm;
import com.xsy.scm.inventory.domain.form.InventoryConversionAuditForm;
import com.xsy.scm.inventory.domain.form.InventoryConversionQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryConversionVO;
import com.xsy.scm.inventory.service.InventoryConversionQueryService;
import com.xsy.scm.inventory.service.InventoryConversionService;
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
 * 规格转换仅处理同仓库内的商品规格折算，跨仓库使用调拨。录入、审批、驳回分别检查权限；只有审批通过才修改库存，禁止无审批权限人员自行放行转换。
 */
@RestController
@RequestMapping("/scm/inventory/conversion")
@Tag(name = "SCM 库存规格转换单")
@RequiredArgsConstructor
public class InventoryConversionController {

    private final InventoryConversionService inventoryConversionService;

    private final InventoryConversionQueryService inventoryConversionQueryService;

    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.CONVERSION_QUERY)
    public ResponseDTO<PageResult<InventoryConversionVO>> query(@Valid @RequestBody InventoryConversionQueryForm form) {
        return ResponseDTO.ok(inventoryConversionQueryService.queryPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(InventoryPermission.CONVERSION_QUERY)
    public ResponseDTO<InventoryConversionVO> detail(@PathVariable("id") Long conversionId) {
        return ResponseDTO.ok(inventoryConversionQueryService.detail(conversionId));
    }

    /**
     * 新建（创建即待审核），返回新单 id。
     */
    @PostMapping("/create")
    @SaCheckPermission(InventoryPermission.CONVERSION_ADD)
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody InventoryConversionAddForm form) {
        return ResponseDTO.ok(inventoryConversionService.create(form));
    }

    /**
     * 改待审核单据（仅 PENDING）。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission(InventoryPermission.CONVERSION_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@PathVariable("id") Long conversionId,
        @Valid @RequestBody InventoryConversionAddForm form) {
        inventoryConversionService.update(conversionId, form);
        return ResponseDTO.ok();
    }

    /**
     * 审批通过：写 {@code CONVERT_OUT} + {@code CONVERT_IN} 流水并调整两边余额。
     *
     * <p>
     * 这是本模块唯一会改变库存的端点，失败整单回滚，不存在「转一半」。请求体必须带上审批人看到的 {@code version}，否则若单据在审批期间被改过会以 40921 失败。
     */
    @PostMapping("/approve/{id}")
    @SaCheckPermission(InventoryPermission.CONVERSION_APPROVE)
    @OperateLog
    public ResponseDTO<String> approve(@PathVariable("id") Long conversionId,
        @Valid @RequestBody InventoryConversionAuditForm form) {
        inventoryConversionService.approve(conversionId, form);
        return ResponseDTO.ok();
    }

    /**
     * 驳回（不产生任何库存影响）；审核意见必填。
     */
    @PostMapping("/reject/{id}")
    @SaCheckPermission(InventoryPermission.CONVERSION_REJECT)
    @OperateLog
    public ResponseDTO<String> reject(@PathVariable("id") Long conversionId,
        @Valid @RequestBody InventoryConversionAuditForm form) {
        inventoryConversionService.reject(conversionId, form);
        return ResponseDTO.ok();
    }

    /**
     * 删除待审核单据（逻辑删）。已审核的单据不可删除。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission(InventoryPermission.CONVERSION_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@PathVariable("id") Long conversionId) {
        inventoryConversionService.delete(conversionId);
        return ResponseDTO.ok();
    }
}
