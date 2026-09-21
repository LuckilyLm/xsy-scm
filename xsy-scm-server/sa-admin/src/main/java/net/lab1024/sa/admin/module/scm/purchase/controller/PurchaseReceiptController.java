package net.lab1024.sa.admin.module.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptBatchDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseReceiptService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购收货端点（W5 Target Design §7.1 的 8 个端点）。
 *
 * <p>`create` 与 `confirm` 要求 `Idempotency-Key` 头（缺失 → 40084，因此声明为
 * {@code required = false}）；`update` 只改备注，靠行级 `@Version` 保证重复提交安全。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/purchase/receipt")
public class PurchaseReceiptController {

    private final PurchaseReceiptService purchaseReceiptService;

    private final PurchaseQueryService purchaseQueryService;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @PostMapping("/query")
    @SaCheckPermission("scm:purchase:receipt:query")
    public ResponseDTO<PageResult<PurchaseReceiptVO>> query(
            @Valid @RequestBody PurchaseReceiptQueryForm form) {
        return ResponseDTO.ok(purchaseQueryService.receiptQuery(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:purchase:receipt:query")
    public ResponseDTO<PurchaseReceiptVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(purchaseQueryService.receiptDetail(id));
    }

    @GetMapping("/item/{receiptId}")
    @SaCheckPermission("scm:purchase:receipt:query")
    public ResponseDTO<List<PurchaseReceiptItemVO>> items(@PathVariable Long receiptId) {
        return ResponseDTO.ok(purchaseQueryService.receiptItems(receiptId));
    }

    // ------------------------------------------------------------------
    // 命令
    // ------------------------------------------------------------------

    @PostMapping("/create")
    @SaCheckPermission("scm:purchase:receipt:add")
    @OperateLog
    public ResponseDTO<PurchaseReceiptVO> create(
            @Valid @RequestBody PurchaseReceiptCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseReceiptService.create(form, idempotencyKey));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:purchase:receipt:update")
    @OperateLog
    public ResponseDTO<PurchaseReceiptVO> update(@Valid @RequestBody PurchaseReceiptUpdateForm form) {
        return ResponseDTO.ok(purchaseReceiptService.update(form));
    }

    @PostMapping("/confirm")
    @SaCheckPermission("scm:purchase:receipt:confirm")
    @OperateLog
    public ResponseDTO<PurchaseReceiptVO> confirm(
            @Valid @RequestBody PurchaseReceiptConfirmForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseReceiptService.confirm(form, idempotencyKey));
    }

    /**
     * 仓库确认入库（B1）：仅 WAREHOUSE_CONFIRM 且 PENDING 的已确认收货单。
     */
    @PostMapping("/putaway")
    @SaCheckPermission("scm:purchase:receipt:putaway")
    @OperateLog
    public ResponseDTO<PurchaseReceiptVO> putaway(
            @Valid @RequestBody PurchaseReceiptPutawayForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseReceiptService.putaway(form, idempotencyKey));
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:purchase:receipt:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody PurchaseReceiptDeleteForm form) {
        purchaseReceiptService.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/batch-delete")
    @SaCheckPermission("scm:purchase:receipt:delete")
    @OperateLog
    public ResponseDTO<String> batchDelete(@Valid @RequestBody PurchaseReceiptBatchDeleteForm form) {
        purchaseReceiptService.batchDelete(form);
        return ResponseDTO.ok();
    }
}
