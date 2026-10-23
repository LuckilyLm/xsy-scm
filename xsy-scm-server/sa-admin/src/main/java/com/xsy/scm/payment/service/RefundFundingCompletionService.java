package com.xsy.scm.payment.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.support.FinancePaymentRefundFact;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmRefundFundingAllocationStatusEnum;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundFundingCompletionService {
    private final PaymentSourceDao paymentSourceDao;
    private final RefundFundingAllocationDao allocationDao;
    private final FinancePaymentService financePaymentService;

    /**
     * 调用方已持有订单锁；渠道回调与余额返还共用同一套完成判定和同一条 Finance 付款记录。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long complete(Long refundId, String operator) {
        var refund = paymentSourceDao.selectOrderRefund(refundId);
        if (refund == null || !refund.completed()) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_SOURCE_INVALID);
        }
        var rows = allocationDao.lockByRefund(refundId);
        if (rows.isEmpty() || rows.stream()
            .anyMatch(row -> !ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name().equals(row.getStatus()))) {
            return null;
        }
        BigDecimal total = rows.stream().map(RefundFundingAllocationEntity::getAllocatedAmount).reduce(BigDecimal.ZERO,
            BigDecimal::add);
        if (total.compareTo(refund.refundAmount()) != 0) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_REFUND_AMOUNT_MISMATCH);
        }
        String reference = rows.stream().map(row -> String.valueOf(row.getResultSourceId()))
            .collect(Collectors.joining(","));
        return financePaymentService.registerFromPaymentRefund(
            FinancePaymentRefundFact.allocated(refundId, refund.customerId(), total, OffsetDateTime.now(), reference),
            operator).getId();
    }
}
