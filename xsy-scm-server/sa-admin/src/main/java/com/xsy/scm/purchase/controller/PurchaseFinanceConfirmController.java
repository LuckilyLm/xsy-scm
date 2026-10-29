package com.xsy.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.purchase.domain.form.PurchaseFinanceConfirmForm;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseFinanceConfirmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PurchaseFinanceConfirmController {
    private final PurchaseFinanceConfirmService confirmationService;

    @PostMapping("/scm/purchase/finance-confirm")
    @SaCheckPermission(PurchasePermission.FINANCE_CONFIRM)
    @OperateLog
    public ResponseDTO<PurchaseReceiptVO> confirm(@Valid @RequestBody PurchaseFinanceConfirmForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(confirmationService.confirm(form, key));
    }
}
