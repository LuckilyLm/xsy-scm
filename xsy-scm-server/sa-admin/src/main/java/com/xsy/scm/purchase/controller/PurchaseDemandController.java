package com.xsy.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandBatchCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandBatchGenerateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandSummaryVO;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandCalculationBatchVO;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandVO;
import com.xsy.scm.purchase.service.PurchaseDemandService;
import com.xsy.scm.purchase.service.PurchaseQueryService;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.common.permission.ScmCrossDomainPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购需求端点（的 3 个端点）。
 *
 * <p>
 * 权限码三段式 `scm:purchase:demand:<action>`；两个写命令都要求 `Idempotency-Key` 头 （缺失 → 40084，由 {@code PurchaseIdempotencyService}
 * 抛出，因此**不能**把该头标成 {@code required = true} —— 那会变成 30001，与 的错误码契约不符）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/purchase/demand")
public class PurchaseDemandController {

    private final PurchaseDemandService purchaseDemandService;
    private final PurchaseDemandCalculationBatchService purchaseDemandCalculationBatchService;

    private final PurchaseQueryService purchaseQueryService;

    @PostMapping("/query")
    @SaCheckPermission(PurchasePermission.DEMAND_QUERY)
    public ResponseDTO<PageResult<PurchaseDemandVO>> query(@Valid @RequestBody PurchaseDemandQueryForm form) {
        return ResponseDTO.ok(purchaseQueryService.demandQuery(form));
    }

    /**
     * 订单汇总 / 库存缺口预览（只读查询工作台：不写业务表、不做幂等）。
     *
     * <p>
     * 返回体带库存现有量与预留量，因此<b>同时</b>要求 {@code scm:purchase:demand:query} 与
     * {@code scm:inventory:balance:query}（{@link SaMode#AND}）：只有采购需求查看权的人不能经此聚合接口 读到库存余额，前端隐藏按钮不作为权限保护。
     */
    @PostMapping("/summary-preview")
    @SaCheckPermission(value = {PurchasePermission.DEMAND_QUERY,
            ScmCrossDomainPermission.INVENTORY_BALANCE_QUERY}, mode = SaMode.AND)
    public ResponseDTO<PageResult<PurchaseDemandSummaryVO>> summaryPreview(
            @Valid @RequestBody PurchaseDemandSummaryPreviewForm form) {
        return ResponseDTO.ok(purchaseQueryService.summaryPreview(form));
    }

    @PostMapping("/batch/create")
    @SaCheckPermission(PurchasePermission.DEMAND_BATCH_CREATE)
    @OperateLog
    public ResponseDTO<PurchaseDemandCalculationBatchVO> createBatch(
            @Valid @RequestBody PurchaseDemandBatchCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseDemandCalculationBatchService.create(form, idempotencyKey));
    }

    @PostMapping("/batch/generate")
    @SaCheckPermission(PurchasePermission.DEMAND_BATCH_GENERATE)
    @OperateLog
    public ResponseDTO<PurchaseDemandService.GenerateResult> generateBatch(
            @Valid @RequestBody PurchaseDemandBatchGenerateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseDemandCalculationBatchService.generate(form, idempotencyKey));
    }

    @PostMapping("/generate")
    @SaCheckPermission(PurchasePermission.DEMAND_GENERATE)
    @OperateLog
    public ResponseDTO<PurchaseDemandService.GenerateResult> generate(
            @Valid @RequestBody PurchaseDemandGenerateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseDemandService.generate(form, idempotencyKey));
    }

    @PostMapping("/allocate")
    @SaCheckPermission(PurchasePermission.DEMAND_ALLOCATE)
    @OperateLog
    public ResponseDTO<PurchaseDemandVO> allocate(@Valid @RequestBody PurchaseDemandAllocateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseDemandService.allocate(form, idempotencyKey));
    }
}
