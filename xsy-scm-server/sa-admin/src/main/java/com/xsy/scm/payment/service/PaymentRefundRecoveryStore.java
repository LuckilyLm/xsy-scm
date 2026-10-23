package com.xsy.scm.payment.service;

import com.xsy.scm.payment.constant.ScmRefundRecoveryResultEnum;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentRefundRecoveryStore {
    private final PaymentRefundDao paymentRefundDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PaymentRefundEntity claim(Long refundId, boolean force, String operator, String reason) {
        if (paymentRefundDao.claimRecovery(refundId, force) != 1) {
            return null;
        }
        paymentRefundDao.appendRecoveryLog(refundId, operator, StringUtils.abbreviate(reason, 500),
            ScmRefundRecoveryResultEnum.STARTED.name(), null, "开始渠道查单");
        return paymentRefundDao.selectById(refundId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finish(PaymentRefundEntity refund, String operator, String reason, ScmRefundRecoveryResultEnum result,
        BigDecimal providerAmount, String detail) {
        long delay = Math.min(3600L, 30L << Math.min(refund.getRecoveryAttempts(), 7));
        String message = StringUtils.abbreviate(detail, 500);
        paymentRefundDao.appendRecoveryLog(refund.getId(), operator, StringUtils.abbreviate(reason, 500), result.name(),
            providerAmount, message);
        paymentRefundDao.finishRecovery(refund.getId(), result.name(), message, delay, refund.getRecoveryAttempts());
    }
}
