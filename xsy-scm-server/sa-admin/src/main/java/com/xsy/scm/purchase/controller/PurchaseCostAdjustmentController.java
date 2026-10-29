package com.xsy.scm.purchase.controller;

import java.util.List;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.purchase.domain.form.PurchaseCostAdjustmentForm;
import com.xsy.scm.purchase.domain.vo.PurchaseCostAdjustmentVO;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseCostAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/purchase/cost-adjustment")
@RequiredArgsConstructor
public class PurchaseCostAdjustmentController {
    private final PurchaseCostAdjustmentService adjustmentService;
    @GetMapping("/options/{orderId}")
    @SaCheckPermission(PurchasePermission.COST_ADJUSTMENT)
    public ResponseDTO<List<PurchaseCostAdjustmentVO>> options(@PathVariable Long orderId) {
        return ResponseDTO.ok(adjustmentService.options(orderId));
    }
    @GetMapping("/history/{orderId}")
    @SaCheckPermission(PurchasePermission.COST_ADJUSTMENT)
    public ResponseDTO<List<PurchaseCostAdjustmentVO>> history(@PathVariable Long orderId) {
        return ResponseDTO.ok(adjustmentService.history(orderId));
    }
    @PostMapping("/add")
    @SaCheckPermission(PurchasePermission.COST_ADJUSTMENT)
    @OperateLog
    public ResponseDTO<PurchaseCostAdjustmentVO> add(@Valid @RequestBody PurchaseCostAdjustmentForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(adjustmentService.add(form, key));
    }
}
