package com.xsy.scm.payment.provider;

import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.dao.PaymentMockLedgerDao;
import com.xsy.scm.payment.domain.entity.PaymentMockLedgerEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟渠道账本的唯一写入口。
 *
 * <p>
 * 独立成 Bean 的唯一理由是事务代理：{@code REQUIRES_NEW} 只有经过 Spring 代理才会真的另开事务。 这个方法若挂在 {@link MockPaymentProvider}
 * 自己身上，{@code createIntent} / {@code refund} 的自调用会绕过代理， 渠道的账就并入本地业务事务、随之一起回滚 —— 「渠道扣了钱、本地没记上」这类对账差异就再也模拟不出来， mock
 * 作为渠道替身的意义也就没了。
 */
@Component
@RequiredArgsConstructor
public class MockPaymentLedgerRecorder {

    private final PaymentMockLedgerDao paymentMockLedgerDao;

    /**
     * 渠道记账。<b>独立事务</b>：渠道的账不随本地事务回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String providerTransactionNo, String providerRefundNo, String direction, BigDecimal amount) {
        PaymentMockLedgerEntity row = new PaymentMockLedgerEntity();
        row.setProvider(ScmPaymentProviderEnum.MOCK.name());
        row.setProviderTransactionNo(providerTransactionNo);
        row.setProviderRefundNo(providerRefundNo);
        row.setDirection(direction);
        row.setAmount(amount);
        row.setBizDate(OffsetDateTime.now().toLocalDate());
        row.setOccurredAt(OffsetDateTime.now());
        row.setCreatedBy("MOCK_PROVIDER");
        paymentMockLedgerDao.insert(row);
    }
}
