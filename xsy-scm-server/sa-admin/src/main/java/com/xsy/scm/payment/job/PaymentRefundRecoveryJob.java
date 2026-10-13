package com.xsy.scm.payment.job;

import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.service.PaymentRefundRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scm.payment.refund-recovery.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRefundRecoveryJob {
    private static final String RECOVERY_DELAY = "${scm.payment.refund-recovery.delay-ms:30000}";
    private static final String INITIAL_DELAY = "${scm.payment.refund-recovery.initial-delay-ms:30000}";
    private final PaymentRefundDao paymentRefundDao;
    private final PaymentRefundRecoveryService recoveryService;

    @Scheduled(fixedDelayString = RECOVERY_DELAY, initialDelayString = INITIAL_DELAY)
    public void recover() {
        for (Long refundId : paymentRefundDao.recoveryCandidates(50)) {
            try {
                recoveryService.recover(refundId, "system:refund-recovery", "定时渠道查单与退款恢复", false);
            } catch (RuntimeException exception) {
                log.warn("退款恢复任务失败：refundId={}", refundId, exception);
            }
        }
    }
}
