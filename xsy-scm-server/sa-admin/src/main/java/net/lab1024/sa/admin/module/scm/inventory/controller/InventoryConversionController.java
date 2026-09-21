package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryConversionVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryConversionQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryConversionService;
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
 * SCM 规格转换单（整件拆零 / 组合拆分）。
 *
 * <p>共 7 个端点：分页 / 详情 / 新建 / 改待审核 / 审批通过 / 驳回 / 删除。
 *
 * <p><b>跨 SKU、同仓库</b>：跨仓搬运是**调拨**（{@code /scm/inventory/transfer}），
 * 不是转换 —— 两者职责必须分清，否则会出现「用转换单搬货」这种绕过调拨在途语义的用法。
 *
 * <p><b>权限</b>：查询 {@code scm:inventory:conversion:query}、新建 {@code :add}、
 * 改待审核 {@code :update}、审批 {@code :approve}、驳回 {@code :reject}、删除 {@code :delete}。
 * 「审批」与「驳回」是两个独立权限（与报损报溢一致）；
 * 「新建」与「审批」也必须分开 —— 折算关系（一箱等于多少 kg）是人工声明的，
 * 由同一个人录单并批准等于没人复核。
 */
@RestController
@RequestMapping("/scm/inventory/conversion")
@Tag(name = "SCM 库存规格转换单")
@RequiredArgsConstructor
public class InventoryConversionController {

    private final InventoryConversionService service;

    private final InventoryConversionQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:conversion:query")
    public ResponseDTO<PageResult<InventoryConversionVO>> query(
            @Valid @RequestBody InventoryConversionQueryForm form) {
        return ResponseDTO.ok(queryService.queryPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:inventory:conversion:query")
    public ResponseDTO<InventoryConversionVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queryService.detail(id));
    }

    /**
     * 新建（创建即待审核），返回新单 id。
     */
    @PostMapping("/create")
    @SaCheckPermission("scm:inventory:conversion:add")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody InventoryConversionAddForm form) {
        return ResponseDTO.ok(service.create(form));
    }

    /**
     * 改待审核单据（仅 PENDING）。
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission("scm:inventory:conversion:update")
    @OperateLog
    public ResponseDTO<String> update(@PathVariable Long id,
                                      @Valid @RequestBody InventoryConversionAddForm form) {
        service.update(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 审批通过：写 {@code CONVERT_OUT} + {@code CONVERT_IN} 流水并调整两边余额。
     *
     * <p>这是本模块唯一会改变库存的端点，失败整单回滚，不存在「转一半」。
     * 请求体必须带上审批人看到的 {@code version}，否则若单据在审批期间被改过会以 40921 失败。
     */
    @PostMapping("/approve/{id}")
    @SaCheckPermission("scm:inventory:conversion:approve")
    @OperateLog
    public ResponseDTO<String> approve(@PathVariable Long id,
                                       @Valid @RequestBody InventoryConversionAuditForm form) {
        service.approve(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 驳回（不产生任何库存影响）；审核意见必填。
     */
    @PostMapping("/reject/{id}")
    @SaCheckPermission("scm:inventory:conversion:reject")
    @OperateLog
    public ResponseDTO<String> reject(@PathVariable Long id,
                                      @Valid @RequestBody InventoryConversionAuditForm form) {
        service.reject(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 删除待审核单据（逻辑删）。已审核的单据不可删除。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("scm:inventory:conversion:delete")
    @OperateLog
    public ResponseDTO<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseDTO.ok();
    }
}
