package net.lab1024.sa.admin.module.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseDemandService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购需求端点（W5 Target Design §7.1 的 3 个端点）。
 *
 * <p>权限码三段式 `scm:purchase:demand:<action>`；两个写命令都要求 `Idempotency-Key` 头
 * （缺失 → 40084，由 {@code PurchaseIdempotencyService} 抛出，因此**不能**把该头标成
 * {@code required = true} —— 那会变成 30001，与 §7.7 的错误码契约不符）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/purchase/demand")
public class PurchaseDemandController {

    private final PurchaseDemandService purchaseDemandService;

    private final PurchaseQueryService purchaseQueryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:purchase:demand:query")
    public ResponseDTO<PageResult<PurchaseDemandVO>> query(@Valid @RequestBody PurchaseDemandQueryForm form) {
        return ResponseDTO.ok(purchaseQueryService.demandQuery(form));
    }

    @PostMapping("/generate")
    @SaCheckPermission("scm:purchase:demand:generate")
    @OperateLog
    public ResponseDTO<PurchaseDemandService.GenerateResult> generate(
            @Valid @RequestBody PurchaseDemandGenerateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseDemandService.generate(form, idempotencyKey));
    }

    @PostMapping("/allocate")
    @SaCheckPermission("scm:purchase:demand:allocate")
    @OperateLog
    public ResponseDTO<PurchaseDemandVO> allocate(
            @Valid @RequestBody PurchaseDemandAllocateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseDemandService.allocate(form, idempotencyKey));
    }
}
