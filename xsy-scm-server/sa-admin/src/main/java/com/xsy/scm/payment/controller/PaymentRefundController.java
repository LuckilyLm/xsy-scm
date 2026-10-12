package com.xsy.scm.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.payment.constant.ScmPaymentPermission;
import com.xsy.scm.payment.domain.form.PaymentRefundCreateForm;
import com.xsy.scm.payment.domain.form.PaymentRefundRecoveryForm;
import com.xsy.scm.payment.domain.vo.PaymentRefundRecoveryLogVO;
import com.xsy.scm.payment.service.PaymentRefundRecoveryService;
import com.xsy.scm.common.constant.ScmOperator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.xsy.scm.payment.domain.form.PaymentRefundQueryForm;
import com.xsy.scm.payment.domain.form.RefundFundingDetailForm;
import com.xsy.scm.payment.domain.form.RefundFundingExecuteForm;
import com.xsy.scm.payment.domain.vo.PaymentRefundVO;
import com.xsy.scm.payment.domain.vo.RefundFundingResultVO;
import com.xsy.scm.payment.service.PaymentQueryService;
import com.xsy.scm.payment.service.PaymentRefundService;
import com.xsy.scm.payment.service.RefundFundingAllocationService;
import com.xsy.scm.payment.support.PaymentVoAssembler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支付退款：发起退款、查退款。 */
@RestController
@RequestMapping("/scm/payment/refund")
@RequiredArgsConstructor
public class PaymentRefundController {

    private final PaymentRefundService paymentRefundService;

    private final PaymentQueryService paymentQueryService;

    private final RefundFundingAllocationService refundFundingAllocationService;

    private final PaymentRefundRecoveryService paymentRefundRecoveryService;

    @PostMapping("/recover")
    @SaCheckPermission(ScmPaymentPermission.REFUND_CREATE)
    @OperateLog
    public ResponseDTO<PaymentRefundVO> recover(@Valid @RequestBody PaymentRefundRecoveryForm form) {
        paymentRefundService.requireVisibleRefund(form.getRefundId());
        return ResponseDTO.ok(PaymentVoAssembler.toRefund(paymentRefundRecoveryService.recover(form.getRefundId(),
                ScmOperator.current(), form.getReason(), true)));
    }

    @GetMapping("/{refundId}/recovery-log")
    @SaCheckPermission(ScmPaymentPermission.REFUND_QUERY)
    public ResponseDTO<List<PaymentRefundRecoveryLogVO>> recoveryLog(@PathVariable Long refundId) {
        return ResponseDTO.ok(paymentRefundService.recoveryLogs(refundId));
    }

    /**
     * 发起退款。
     *
     * <p>
     * 需要 {@code Idempotency-Key}：退款是出款动作，重复提交的代价是真金白银。 同键同内容重放首次结果，不重复向渠道发起。
     */
    @PostMapping("/create")
    @SaCheckPermission(ScmPaymentPermission.REFUND_CREATE)
    @OperateLog
    public ResponseDTO<PaymentRefundVO> create(@Valid @RequestBody PaymentRefundCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(PaymentVoAssembler.toRefund(paymentRefundService.create(form, key)));
    }

    @PostMapping("/funding/execute")
    @SaCheckPermission(ScmPaymentPermission.REFUND_CREATE)
    @OperateLog
    public ResponseDTO<RefundFundingResultVO> executeFunding(@Valid @RequestBody RefundFundingExecuteForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(refundFundingAllocationService.execute(form, key));
    }

    @PostMapping("/funding/detail")
    @SaCheckPermission(ScmPaymentPermission.REFUND_QUERY)
    public ResponseDTO<RefundFundingResultVO> fundingDetail(@Valid @RequestBody RefundFundingDetailForm form) {
        return ResponseDTO.ok(refundFundingAllocationService.detail(form.getRefundId()));
    }

    @PostMapping("/query")
    @SaCheckPermission(ScmPaymentPermission.REFUND_QUERY)
    public ResponseDTO<PageResult<PaymentRefundVO>> query(@Valid @RequestBody PaymentRefundQueryForm form) {
        return ResponseDTO.ok(paymentQueryService.refundPage(form));
    }
}
