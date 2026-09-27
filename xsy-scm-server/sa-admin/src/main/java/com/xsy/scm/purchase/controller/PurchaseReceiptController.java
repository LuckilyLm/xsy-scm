package com.xsy.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptBatchDeleteForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptDeleteForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptItemWorkbenchQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptUpdateForm;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemWorkbenchVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.service.PurchaseQueryService;
import com.xsy.scm.purchase.service.PurchaseReceiptService;
import com.xsy.scm.purchase.permission.PurchasePermission;
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
 * 采购收货端点（的 8 个端点）。
 *
 * <p>
 * `create` 与 `confirm` 要求 `Idempotency-Key` 头（缺失 → 40084，因此声明为 {@code required = false}）；`update` 只改备注，靠行级 `@Version`
 * 保证重复提交安全。
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
    @SaCheckPermission(PurchasePermission.RECEIPT_QUERY)
    public ResponseDTO<
            PageResult<
                    PurchaseReceiptVO>> query(@Valid @RequestBody PurchaseReceiptQueryForm form) {
        return ResponseDTO.ok(purchaseQueryService.receiptQuery(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(PurchasePermission.RECEIPT_QUERY)
    public ResponseDTO<
            PurchaseReceiptVO> detail(@PathVariable("id") Long receiptId) {
        return ResponseDTO.ok(purchaseQueryService.receiptDetail(receiptId));
    }

    @GetMapping("/item/{receiptId}")
    @SaCheckPermission(PurchasePermission.RECEIPT_QUERY)
    public ResponseDTO<
            List<
                    PurchaseReceiptItemVO>> items(@PathVariable Long receiptId) {
        return ResponseDTO.ok(purchaseQueryService.receiptItems(receiptId));
    }

    /**
     * 按商品收货工作台（只读）：跨可收货采购单按 SKU×采购单位 汇总计划 / 已收 / 欠收 / 超收， 复用
     * {@code scm:purchase:receipt:query}。它只是视图，不新增收货事实，确认收货仍走各收货单既有端点。
     */
    @PostMapping("/item-workbench/query")
    @SaCheckPermission(PurchasePermission.RECEIPT_QUERY)
    public ResponseDTO<
            PageResult<
                    PurchaseReceiptItemWorkbenchVO>> itemWorkbench(
                            @Valid @RequestBody PurchaseReceiptItemWorkbenchQueryForm form) {
        return ResponseDTO.ok(purchaseQueryService.receiptItemWorkbench(form));
    }

    // ------------------------------------------------------------------
    // 命令
    // ------------------------------------------------------------------

    @PostMapping("/create")
    @SaCheckPermission(PurchasePermission.RECEIPT_ADD)
    @OperateLog
    public ResponseDTO<
            PurchaseReceiptVO> create(@Valid @RequestBody PurchaseReceiptCreateForm form,
                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseReceiptService.create(form, idempotencyKey));
    }

    @PostMapping("/update")
    @SaCheckPermission(PurchasePermission.RECEIPT_UPDATE)
    @OperateLog
    public ResponseDTO<
            PurchaseReceiptVO> update(@Valid @RequestBody PurchaseReceiptUpdateForm form) {
        return ResponseDTO.ok(purchaseReceiptService.update(form));
    }

    @PostMapping("/confirm")
    @SaCheckPermission(PurchasePermission.RECEIPT_CONFIRM)
    @OperateLog
    public ResponseDTO<
            PurchaseReceiptVO> confirm(@Valid @RequestBody PurchaseReceiptConfirmForm form,
                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseReceiptService.confirm(form, idempotencyKey));
    }

    /**
     * 仓库确认入库：仅 WAREHOUSE_CONFIRM 且 PENDING 的已确认收货单。
     */
    @PostMapping("/putaway")
    @SaCheckPermission(PurchasePermission.RECEIPT_PUTAWAY)
    @OperateLog
    public ResponseDTO<
            PurchaseReceiptVO> putaway(@Valid @RequestBody PurchaseReceiptPutawayForm form,
                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseReceiptService.putaway(form, idempotencyKey));
    }

    @PostMapping("/delete")
    @SaCheckPermission(PurchasePermission.RECEIPT_DELETE)
    @OperateLog
    public ResponseDTO<
            String> delete(@Valid @RequestBody PurchaseReceiptDeleteForm form) {
        purchaseReceiptService.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/batch-delete")
    @SaCheckPermission(PurchasePermission.RECEIPT_DELETE)
    @OperateLog
    public ResponseDTO<
            String> batchDelete(@Valid @RequestBody PurchaseReceiptBatchDeleteForm form) {
        purchaseReceiptService.batchDelete(form);
        return ResponseDTO.ok();
    }
}
