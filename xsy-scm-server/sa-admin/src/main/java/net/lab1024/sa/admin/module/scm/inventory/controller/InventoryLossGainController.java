package net.lab1024.sa.admin.module.scm.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryLossGainVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainService;
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
 * SCM 库存报损报溢单。
 *
 * <p>共 7 个端点：分页 / 详情 / 新建 / 改待审核 / 审批通过 / 驳回 / 删除。
 *
 * <p><b>权限划分</b>：查询 {@code scm:inventory:loss-gain:query}、
 * 新建 {@code :add}、改待审核 {@code :update}、审批 {@code :approve}、驳回 {@code :reject}、
 * 删除 {@code :delete}。
 *
 * <p><b>「审批」与「驳回」是两个独立权限</b>（与参考项目一致）：允许主管审批、
 * 由另一角色驳回是常见分工；合成一个「审核」权限会让这两件事无法分权。
 * 「新建」与「审批」也必须分开 —— 报损是把货从账上抹掉的动作，
 * 由同一个人录单并批准就失去了制衡。
 */
@RestController
@RequestMapping("/scm/inventory/loss-gain")
@Tag(name = "SCM 库存报损报溢单")
@RequiredArgsConstructor
public class InventoryLossGainController {

    private final InventoryLossGainService service;

    private final InventoryLossGainQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:inventory:loss-gain:query")
    public ResponseDTO<PageResult<InventoryLossGainVO>> query(@Valid @RequestBody InventoryLossGainQueryForm form) {
        return ResponseDTO.ok(queryService.queryPage(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:inventory:loss-gain:query")
    public ResponseDTO<InventoryLossGainVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queryService.detail(id));
    }

    /** 新建报损报溢单（创建即待审核），返回新单 id。 */
    @PostMapping("/create")
    @SaCheckPermission("scm:inventory:loss-gain:add")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody InventoryLossGainAddForm form) {
        return ResponseDTO.ok(service.create(form));
    }

    /** 改待审核单据（仅 PENDING）。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("scm:inventory:loss-gain:update")
    @OperateLog
    public ResponseDTO<String> update(@PathVariable Long id,
                                      @Valid @RequestBody InventoryLossGainAddForm form) {
        service.update(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 审批通过：写 {@code LOSS_REPORT} / {@code GAIN_REPORT} 流水并调整余额。
     *
     * <p>这是本模块唯一会改变库存的端点，失败整单回滚，不存在「报一半」。
     * 请求体必须带上审批人看到的 {@code version}，否则若单据在审批期间被改过会以 40921 失败。
     */
    @PostMapping("/approve/{id}")
    @SaCheckPermission("scm:inventory:loss-gain:approve")
    @OperateLog
    public ResponseDTO<String> approve(@PathVariable Long id,
                                       @Valid @RequestBody InventoryLossGainAuditForm form) {
        service.approve(id, form);
        return ResponseDTO.ok();
    }

    /** 驳回（不产生任何库存影响）；审核意见必填。 */
    @PostMapping("/reject/{id}")
    @SaCheckPermission("scm:inventory:loss-gain:reject")
    @OperateLog
    public ResponseDTO<String> reject(@PathVariable Long id,
                                      @Valid @RequestBody InventoryLossGainAuditForm form) {
        service.reject(id, form);
        return ResponseDTO.ok();
    }

    /** 删除待审核单据（逻辑删）。已审核的单据不可删除。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("scm:inventory:loss-gain:delete")
    @OperateLog
    public ResponseDTO<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseDTO.ok();
    }
}
