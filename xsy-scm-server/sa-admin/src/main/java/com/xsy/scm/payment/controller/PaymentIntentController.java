package com.xsy.scm.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.payment.constant.ScmPaymentPermission;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.domain.form.PaymentIntentQueryForm;
import com.xsy.scm.payment.domain.form.PaymentTransactionQueryForm;
import com.xsy.scm.payment.domain.vo.PaymentIntentVO;
import com.xsy.scm.payment.domain.vo.PaymentTransactionVO;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.service.PaymentQueryService;
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
 * 支付交易：创建支付意图、查意图与交易。
 *
 * <p>
 * 本阶段的回调入口**不是**这个 Controller：渠道回调走受控的模拟入口（见
 * {@code PaymentCallbackController}），登录态 + 权限，不开放匿名 URL。
 */
@RestController
@RequestMapping("/scm/payment/intent")
@RequiredArgsConstructor
public class PaymentIntentController {

    private final PaymentIntentService paymentIntentService;

    private final PaymentQueryService paymentQueryService;

    /**
     * 创建支付意图并向渠道发起。
     *
     * <p>
     * 应付金额由入参显式给出；本接口**不做**「按订单金额自动算」的推断。
     */
    @PostMapping("/create")
    @SaCheckPermission(ScmPaymentPermission.INTENT_CREATE)
    @OperateLog
    public ResponseDTO<PaymentIntentVO> create(@Valid @RequestBody PaymentIntentCreateForm form) {
        return ResponseDTO.ok(PaymentVoAssembler.toIntent(paymentIntentService.create(form)));
    }

    @PostMapping("/query")
    @SaCheckPermission(ScmPaymentPermission.TRANSACTION_QUERY)
    public ResponseDTO<PageResult<PaymentIntentVO>> query(@Valid @RequestBody PaymentIntentQueryForm form) {
        return ResponseDTO.ok(paymentQueryService.intentPage(form));
    }

    /** 意图详情（含其下交易流水）。 */
    @GetMapping("/detail/{id}")
    @SaCheckPermission(ScmPaymentPermission.TRANSACTION_QUERY)
    public ResponseDTO<PaymentIntentVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(paymentQueryService.intentDetail(id));
    }

    @PostMapping("/transaction/query")
    @SaCheckPermission(ScmPaymentPermission.TRANSACTION_QUERY)
    public ResponseDTO<PageResult<PaymentTransactionVO>> transactionQuery(
            @Valid @RequestBody PaymentTransactionQueryForm form) {
        return ResponseDTO.ok(paymentQueryService.transactionPage(form));
    }
}
