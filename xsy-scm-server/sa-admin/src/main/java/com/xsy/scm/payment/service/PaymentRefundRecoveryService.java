package com.xsy.scm.payment.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.notification.service.ScmNotificationService;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.constant.ScmPaymentRefundStatusEnum;
import com.xsy.scm.payment.constant.ScmRefundRecoveryResultEnum;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.message.constant.MessageTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundRecoveryService {
    private final PaymentRefundRecoveryStore recoveryStore;
    private final PaymentRefundDao paymentRefundDao;
    private final PaymentTransactionDao paymentTransactionDao;
    private final PaymentRefundService paymentRefundService;
    private final ScmPaymentProviderRegistry providerRegistry;
    private final ScmNotificationService notificationService;

    /** 渠道调用只能发生在持久化意图提交之后，禁止被外层业务事务包裹。 */
    @Transactional(propagation = Propagation.NEVER)
    public PaymentRefundEntity recover(Long refundId, String operator, String reason, boolean force) {
        PaymentRefundEntity refund = recoveryStore.claim(refundId, force, operator, reason);
        if (refund == null) {
            return paymentRefundDao.selectById(refundId);
        }
        ScmRefundRecoveryResultEnum result = ScmRefundRecoveryResultEnum.ERROR;
        BigDecimal providerAmount = null;
        String detail = null;
        try {
            ScmPaymentProvider provider = providerRegistry.require(refund.getProvider());
            var transaction = paymentTransactionDao.selectById(refund.getTransactionId());
            if (transaction == null) {
                throw new ScmBusinessException(PaymentErrorCode.PAYMENT_TRANSACTION_NOT_FOUND);
            }
            var outcome = provider.queryRefund(refund.getRefundNo(), refund.getProviderRefundNo());
            if (outcome == null) {
                if (ScmPaymentRefundStatusEnum.SUCCEEDED.name().equals(refund.getStatus())
                        || ScmPaymentRefundStatusEnum.FAILED.name().equals(refund.getStatus())) {
                    throw new IllegalStateException("渠道未查到已终结的退款，需人工对账，禁止重新出款");
                }
                outcome = provider.refund(new ScmPaymentProvider.RefundRequest(refund.getRefundNo(),
                        transaction.getProviderTransactionNo(), refund.getAmount(), refund.getReason(),
                        ScmPaymentMockScenarioEnum.of(refund.getMockScenario())));
            }
            providerAmount = outcome.amount();
            paymentRefundService.applyOutcome(refundId, outcome.outcome(), outcome.providerRefundNo(), outcome.amount(),
                    outcome.failureCode(), outcome.failureMessage(), operator);
            if (outcome.outcome() == ScmPaymentProvider.Outcome.SUCCEEDED
                    && (providerAmount == null || providerAmount.compareTo(refund.getAmount()) != 0)) {
                result = ScmRefundRecoveryResultEnum.AMOUNT_MISMATCH;
                detail = "渠道实退与申请金额不一致，保留渠道事实，禁止自动补退或冲账";
            } else {
                result = switch (outcome.outcome()) {
                    case SUCCEEDED -> ScmRefundRecoveryResultEnum.SUCCEEDED;
                    case FAILED -> ScmRefundRecoveryResultEnum.FAILED;
                    case PENDING -> ScmRefundRecoveryResultEnum.PENDING;
                };
                detail = outcome.failureMessage();
            }
        } catch (RuntimeException exception) {
            if (exception instanceof ScmBusinessException businessException
                    && businessException.getErrorCode() == PaymentErrorCode.PAYMENT_REFUND_AMOUNT_MISMATCH) {
                result = ScmRefundRecoveryResultEnum.AMOUNT_MISMATCH;
            }
            // 网络或落账异常不等于渠道拒绝：保留原退款号，下一次先查单，绝不另建退款。
            detail = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            log.warn("退款恢复未完成：refundId={}，保留原渠道幂等号", refundId, exception);
        }
        recoveryStore.finish(refund, operator, reason, result, providerAmount, detail);
        notifyIfNeeded(refund, result);
        return paymentRefundDao.selectById(refundId);
    }

    private void notifyIfNeeded(PaymentRefundEntity refund, ScmRefundRecoveryResultEnum result) {
        boolean abnormal = result == ScmRefundRecoveryResultEnum.AMOUNT_MISMATCH
                || result == ScmRefundRecoveryResultEnum.FAILED
                || (result != ScmRefundRecoveryResultEnum.SUCCEEDED && refund.getRecoveryAttempts() >= 5);
        if (!abnormal) {
            return;
        }
        String prefix = UserTypeEnum.ADMIN_EMPLOYEE.getValue() + ":";
        if (refund.getCreatedBy() == null || !refund.getCreatedBy().startsWith(prefix)) {
            log.warn("退款需要人工核对：refundId={} result={}", refund.getId(), result);
            return;
        }
        try {
            Long employeeId = Long.valueOf(refund.getCreatedBy().substring(prefix.length()));
            notificationService.sendOnce("PAYMENT_REFUND_ATTENTION:" + refund.getId() + ":" + result.name(),
                    "PAYMENT_REFUND_ATTENTION", MessageTypeEnum.MAIL.getValue(), employeeId, refund.getId(), "退款需要核对",
                    "退款单 " + refund.getRefundNo() + " 需要核对渠道状态与实退金额，请查看退款恢复记录。");
        } catch (RuntimeException exception) {
            log.warn("退款异常通知失败：refundId={}", refund.getId(), exception);
        }
    }
}
