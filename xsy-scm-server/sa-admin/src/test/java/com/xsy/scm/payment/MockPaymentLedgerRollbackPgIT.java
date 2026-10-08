package com.xsy.scm.payment;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.dao.PaymentMockLedgerDao;
import com.xsy.scm.payment.domain.entity.PaymentMockLedgerEntity;
import com.xsy.scm.payment.provider.MockPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模拟渠道账本的事务独立性（FIX-02，PG IT，无外层事务）。
 *
 * <p>渠道是另一个系统：它的账（{@code payment_mock_ledger}）用 {@code REQUIRES_NEW} 写，
 * 不随本地业务事务回滚 —— 否则「渠道扣了钱、本地没记上」这类对账差异根本模拟不出来。
 * 这个注解曾经挂在 {@code MockPaymentProvider} 自己身上，同类自调用绕过代理而静默失效；
 * 本类验证修复后：本地事务回滚，渠道账照常留下。因为验证依赖<b>真实提交</b>，
 * 断言一律用唯一单号或相对调用前的基线测量，不按全表零断言。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("模拟渠道账本不随本地事务回滚（FIX-02，PG IT，无外层事务）")
class MockPaymentLedgerRollbackPgIT extends ScmW5PgITBase {

    @Autowired
    private MockPaymentProvider mockPaymentProvider;

    @Autowired
    private PaymentMockLedgerDao paymentMockLedgerDao;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    @Test
    @DisplayName("收款：本地事务回滚后渠道收款账仍在，渠道拒付不记账")
    void receiptLedgerSurvivesBusinessRollback() {
        String intentNo = "IT-ROLLBACK-" + UUID.randomUUID();
        String providerTransactionNo = "MOCK-TXN-" + intentNo;

        TransactionTemplate businessTx = new TransactionTemplate(transactionManager);
        businessTx.executeWithoutResult(status -> {
            mockPaymentProvider.createIntent(new ScmPaymentProvider.IntentRequest(intentNo,
                    new BigDecimal("12.0000"), "FIX-02 回滚验证", ScmPaymentMockScenarioEnum.SUCCESS));
            status.setRollbackOnly();
        });

        PaymentMockLedgerEntity row = paymentMockLedgerDao.selectInByProviderTransactionNo(providerTransactionNo);
        assertThat(row).as("本地事务已回滚，渠道账必须仍在（REQUIRES_NEW 经代理生效）").isNotNull();
        assertThat(row.getDirection()).isEqualTo("IN");
        assertThat(row.getAmount()).isEqualByComparingTo("12.0000");

        // 渠道拒付：渠道侧不记账，本地回滚后同样什么都查不到
        String declinedIntentNo = intentNo + "-DECLINED";
        businessTx.executeWithoutResult(status -> {
            mockPaymentProvider.createIntent(new ScmPaymentProvider.IntentRequest(declinedIntentNo,
                    new BigDecimal("12.0000"), "FIX-02 拒付", ScmPaymentMockScenarioEnum.FAILURE));
            status.setRollbackOnly();
        });
        assertThat(paymentMockLedgerDao.selectInByProviderTransactionNo("MOCK-TXN-" + declinedIntentNo))
                .as("渠道拒付不记收款账").isNull();
    }

    @Test
    @DisplayName("退款：本地事务回滚后渠道退款账仍在")
    void refundLedgerSurvivesBusinessRollback() {
        String providerTransactionNo = "MOCK-TXN-IT-" + UUID.randomUUID();
        String refundNo = "IT-REFUND-" + UUID.randomUUID();
        int committedBefore = countRows("provider_transaction_no", providerTransactionNo);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            mockPaymentProvider.refund(new ScmPaymentProvider.RefundRequest(refundNo, providerTransactionNo,
                    new BigDecimal("5.0000"), "FIX-02 回滚验证", ScmPaymentMockScenarioEnum.SUCCESS));
            status.setRollbackOnly();
        });

        assertThat(countRows("provider_transaction_no", providerTransactionNo))
                .as("本地事务已回滚，渠道退款账必须仍在").isEqualTo(committedBefore + 1);
        // 渠道退款号带 MOCK-REFUND- 前缀（provider 自己生成）；用裸 refundNo 查会漏，因此按前缀拼。
        assertThat(countRows("provider_refund_no", "MOCK-REFUND-" + refundNo)).isEqualTo(1);
    }

    private int countRows(String column, String value) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM payment_mock_ledger WHERE " + column + " = ?", Integer.class, value);
        return count == null ? 0 : count;
    }
}
