package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.finance.domain.form.FinancePayableRedForm;
import com.xsy.scm.finance.domain.vo.FinancePayableRedVO;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinancePayableService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 手工红字应付入口。 */
@RestController
@RequestMapping("/scm/finance/payable")
@Tag(name = "SCM 财务应付")
@RequiredArgsConstructor
public class FinancePayableController {

    private final FinancePayableService financePayableService;

    @PostMapping("/red")
    @SaCheckPermission(FinancePermission.PAYABLE_RED)
    @OperateLog
    public ResponseDTO<FinancePayableRedVO> red(@Valid @RequestBody FinancePayableRedForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financePayableService.red(form, idempotencyKey));
    }
}
