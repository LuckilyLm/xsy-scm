package com.xsy.scm.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.payment.constant.ScmPaymentPermission;
import com.xsy.scm.payment.domain.form.PaymentReconciliationQueryForm;
import com.xsy.scm.payment.domain.form.PaymentReconciliationRunForm;
import com.xsy.scm.payment.domain.vo.PaymentReconciliationVO;
import com.xsy.scm.payment.service.PaymentQueryService;
import com.xsy.scm.payment.service.PaymentReconciliationService;
import com.xsy.scm.payment.support.PaymentVoAssembler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付对账：执行对账、查结论。
 *
 * <p>
 * 对账<b>只发现差异、不自动修复</b>：自动改交易状态会让审计链说不清「这条是渠道说的、 还是对账程序改的」。差异以事实形式落库，后台展示、人工处理。
 */
@RestController
@RequestMapping("/scm/payment/reconciliation")
@RequiredArgsConstructor
public class PaymentReconciliationController {

    private final PaymentReconciliationService paymentReconciliationService;

    private final PaymentQueryService paymentQueryService;

    @PostMapping("/run")
    @SaCheckPermission(ScmPaymentPermission.RECONCILIATION_RUN)
    @OperateLog
    public ResponseDTO<PaymentReconciliationVO> run(@Valid @RequestBody PaymentReconciliationRunForm form) {
        return ResponseDTO.ok(PaymentVoAssembler
                .toReconciliation(paymentReconciliationService.run(form.getProvider(), form.getBizDate())));
    }

    @PostMapping("/query")
    @SaCheckPermission(ScmPaymentPermission.RECONCILIATION_QUERY)
    public ResponseDTO<PageResult<PaymentReconciliationVO>> query(
            @Valid @RequestBody PaymentReconciliationQueryForm form) {
        return ResponseDTO.ok(paymentQueryService.reconciliationPage(form));
    }

    /** 对账详情（含逐条差异；平账批次 items 为空）。 */
    @GetMapping("/detail/{id}")
    @SaCheckPermission(ScmPaymentPermission.RECONCILIATION_QUERY)
    public ResponseDTO<PaymentReconciliationVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(paymentQueryService.reconciliationDetail(id));
    }
}
