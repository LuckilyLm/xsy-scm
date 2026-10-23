package com.xsy.scm.payment.provider;

import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.dao.PaymentMockLedgerDao;
import com.xsy.scm.payment.domain.entity.PaymentMockLedgerEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟渠道账本通过独立事务写入，不随本地支付事务回滚；这用于测试「渠道成功但本地失败」的对账差异。
 */
@Component
@RequiredArgsConstructor
public class MockPaymentLedgerRecorder {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PaymentMockLedgerDao paymentMockLedgerDao;

    /**
     * 渠道记账。独立事务：渠道的账不随本地事务回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String providerTransactionNo, String providerRefundNo, String direction, BigDecimal amount) {
        if ("OUT".equals(direction)) {
            paymentMockLedgerDao.lockRefundReference(providerRefundNo);
            var existing = paymentMockLedgerDao.selectOutByProviderRefundNo(providerRefundNo);
            if (!existing.isEmpty()) {
                if (existing.stream().anyMatch(row -> !providerTransactionNo.equals(row.getProviderTransactionNo())
                    || amount.compareTo(row.getAmount()) != 0)) {
                    throw new IllegalStateException("同一模拟退款号的交易或金额不一致");
                }
                return;
            }
        }
        PaymentMockLedgerEntity row = new PaymentMockLedgerEntity();
        row.setProvider(ScmPaymentProviderEnum.MOCK.name());
        row.setProviderTransactionNo(providerTransactionNo);
        row.setProviderRefundNo(providerRefundNo);
        row.setDirection(direction);
        row.setAmount(amount);
        OffsetDateTime occurredAt = OffsetDateTime.now(BUSINESS_ZONE);
        row.setBizDate(occurredAt.toLocalDate());
        row.setOccurredAt(occurredAt);
        row.setCreatedBy("MOCK_PROVIDER");
        paymentMockLedgerDao.insert(row);
    }
}
