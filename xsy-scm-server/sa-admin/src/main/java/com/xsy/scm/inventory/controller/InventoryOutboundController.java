package com.xsy.scm.inventory.controller;

import com.xsy.scm.inventory.permission.InventoryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.domain.form.InventoryOutboundAddForm;
import com.xsy.scm.inventory.domain.form.InventoryOutboundQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryOutboundVO;
import com.xsy.scm.inventory.service.InventoryOutboundQueryService;
import com.xsy.scm.inventory.service.InventoryOutboundService;
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
 * SCM 库存出库单（独立出库单）。
 *
 * <p>
 * 共 7 个端点：分页 / 详情 / 新建 / 改草稿 / 确认出库 / 取消 / 删除。
 *
 * <p>
 * <b>权限划分</b>：查询 {@code scm:inventory:outbound:query}、 新建 {@code :add}、改草稿 {@code :update}、确认出库 {@code :confirm}、删除
 * {@code :delete}。 「确认出库」是**独立的权限**而不是复用 {@code :update} —— 确认会真实扣减库存并写不可逆流水， 与「改个草稿」不是同一量级的操作，允许仓管录单但由主管确认是完全合理的分工。
 */
@RestController
@RequestMapping("/scm/inventory/outbound")
@Tag(name = "SCM 库存出库单")
@RequiredArgsConstructor
public class InventoryOutboundController {

    private final InventoryOutboundService inventoryOutboundService;

    private final InventoryOutboundQueryService inventoryOutboundQueryService;

    @PostMapping("/query")
    @SaCheckPermission(InventoryPermission.OUTBOUND_QUERY)
    public ResponseDTO<
            PageResult<
                    InventoryOutboundVO>> query(@Valid @RequestBody InventoryOutboundQueryForm form) {
        return ResponseDTO.ok(inventoryOutboundQueryService.queryPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(InventoryPermission.OUTBOUND_QUERY)
    public ResponseDTO<
            InventoryOutboundVO> detail(@PathVariable("id") Long outboundId) {
        return ResponseDTO.ok(inventoryOutboundQueryService.detail(outboundId));
    }

    /**
     * 新建草稿出库单，返回新单 id。
     */
    @PostMapping("/create")
    @SaCheckPermission(InventoryPermission.OUTBOUND_ADD)
    @OperateLog
    public ResponseDTO<
            Long> create(@Valid @RequestBody InventoryOutboundAddForm form) {
        return ResponseDTO.ok(inventoryOutboundService.create(form));
    }

    /**
     * 改草稿（仅 DRAFT）。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission(InventoryPermission.OUTBOUND_UPDATE)
    @OperateLog
    public ResponseDTO<
            String> update(@PathVariable("id") Long outboundId, @Valid @RequestBody InventoryOutboundAddForm form) {
        inventoryOutboundService.update(outboundId, form);
        return ResponseDTO.ok();
    }

    /**
     * 确认出库：写 SALES_OUT 流水并扣减余额。
     *
     * <p>
     * 这是本模块唯一会改变库存的端点，失败整单回滚，不存在「出一半」。
     */
    @PostMapping("/confirm/{id}")
    @SaCheckPermission(InventoryPermission.OUTBOUND_CONFIRM)
    @OperateLog
    public ResponseDTO<
            String> confirm(@PathVariable("id") Long outboundId) {
        inventoryOutboundService.confirm(outboundId);
        return ResponseDTO.ok();
    }

    /**
     * 取消草稿（不产生任何库存影响）。
     */
    @PostMapping("/cancel/{id}")
    @SaCheckPermission(InventoryPermission.OUTBOUND_UPDATE)
    @OperateLog
    public ResponseDTO<
            String> cancel(@PathVariable("id") Long outboundId) {
        inventoryOutboundService.cancel(outboundId);
        return ResponseDTO.ok();
    }

    /**
     * 删除草稿（逻辑删）。已确认的单不可删。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission(InventoryPermission.OUTBOUND_DELETE)
    @OperateLog
    public ResponseDTO<
            String> delete(@PathVariable("id") Long outboundId) {
        inventoryOutboundService.delete(outboundId);
        return ResponseDTO.ok();
    }
}
