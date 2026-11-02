package com.xsy.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.purchase.domain.form.PurchaseFinanceConfirmForm;
import com.xsy.scm.purchase.domain.vo.PurchaseAccountVO;
import com.xsy.scm.purchase.domain.vo.PurchaseShareFeedbackVO;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseFinanceConfirmService;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PurchaseFinanceConfirmController {
    private final PurchaseFinanceConfirmService confirmationService;

    @GetMapping("/scm/purchase/finance-confirm/{orderId}/feedback")
    @SaCheckPermission(PurchasePermission.FINANCE_CONFIRM)
    public ResponseDTO<List<PurchaseShareFeedbackVO>> feedback(@PathVariable Long orderId) {
        return ResponseDTO.ok(confirmationService.feedback(orderId));
    }

    @PostMapping("/scm/purchase/finance-confirm")
    @SaCheckPermission(PurchasePermission.FINANCE_CONFIRM)
    @OperateLog
    public ResponseDTO<PurchaseAccountVO> confirm(@Valid @RequestBody PurchaseFinanceConfirmForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(confirmationService.confirm(form, key));
    }
}
