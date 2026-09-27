package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.domain.form.FinancePaymentAddForm;
import com.xsy.scm.finance.domain.vo.FinancePaymentVO;
import com.xsy.scm.finance.service.FinancePaymentService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务付款登记入口。
 *
 * <p>
 * 付款查询、导出与反向动作由独立命令提供，不复用本登记端点的权限。
 *
 * <p>
 * 付款是资金动作，重复请求会重复付出，所以必须带 {@code Idempotency-Key}： 同键同内容重放首次结果，不产生第二张付款单。退款付款另外由来源唯一索引兜底 （两个不同键并发付同一张退款，库里仍只有一笔）。
 */
@RestController
@RequestMapping("/scm/finance/payment")
@Tag(name = "SCM 财务付款")
@RequiredArgsConstructor
public class FinancePaymentController {

    private final FinancePaymentService financePaymentService;

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.PAYMENT_ADD)
    @OperateLog
    public ResponseDTO<FinancePaymentVO> add(@Valid @RequestBody FinancePaymentAddForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financePaymentService.add(form, idempotencyKey));
    }
}
