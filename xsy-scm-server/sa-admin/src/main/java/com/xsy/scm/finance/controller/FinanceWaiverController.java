package com.xsy.scm.finance.controller;

import com.xsy.scm.finance.domain.form.FinanceWaiverAddForm;
import com.xsy.scm.finance.domain.form.FinanceWaiverReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceWaiverVO;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceWaiverService;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抹零登记与反向入口。
 */
@RestController
@RequestMapping("/scm/finance/waiver")
@Tag(name = "SCM 财务抹零")
@RequiredArgsConstructor
public class FinanceWaiverController {

    private final FinanceWaiverService financeWaiverService;

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.WAIVER_ADD)
    @OperateLog
    public ResponseDTO<FinanceWaiverVO> add(@Valid @RequestBody FinanceWaiverAddForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeWaiverService.add(form, idempotencyKey));
    }

    @PostMapping("/reverse")
    @SaCheckPermission(FinancePermission.WAIVER_REVERSE)
    @OperateLog
    public ResponseDTO<FinanceWaiverVO> reverse(@Valid @RequestBody FinanceWaiverReverseForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeWaiverService.reverse(form, idempotencyKey));
    }
}
