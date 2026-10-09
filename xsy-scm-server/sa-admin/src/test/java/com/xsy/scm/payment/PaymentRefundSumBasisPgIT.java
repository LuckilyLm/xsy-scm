package com.xsy.scm.payment;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 已成功退款的累计口径（PAY-02，PG IT）。
 *
 * <p><b>缺陷</b>：{@code sumSucceededByTransaction} 原先 {@code SUM(amount)}，即按<b>申请额</b>
 * 累计。而退款服务允许渠道实退（{@code provider_amount}）与申请额不同（差异退款的正常情形），
 * 于是「可退本金 = 渠道实收 − 已退合计」会算错：渠道少退时高估可退额度、多退时低估。
 *
 * <p>本类钉住累计按<b>渠道实退额</b>取数，并覆盖 {@code provider_amount} 缺失时的回退，
 * 避免该行被 {@code SUM} 静默丢成 0。
 */
@DisplayName("已成功退款累计口径（PAY-02，PG IT）")
class PaymentRefundSumBasisPgIT extends ScmW5PgITBase {

    @Autowired
    private PaymentRefundDao paymentRefundDao;

    @Test
    @DisplayName("差异退款：累计取渠道实退额，而不是申请额")
    void sumsProviderAmountNotRequestedAmount() {
        long transactionId = insertTransaction();
        // 申请退 100，渠道实退 90（差异退款）；再申请退 50，渠道实退 45。
        insertRefund(transactionId, new BigDecimal("100.0000"), new BigDecimal("90.0000"), "SUCCEEDED");
        insertRefund(transactionId, new BigDecimal("50.0000"), new BigDecimal("45.0000"), "SUCCEEDED");

        BigDecimal sum = paymentRefundDao.sumSucceededByTransaction(transactionId);

        assertThat(sum)
                .as("必须按渠道实退额累计（90 + 45），按申请额会得到 150")
                .isEqualByComparingTo("135.0000");
    }

    @Test
    @DisplayName("只计 SUCCEEDED：进行中与失败的退款不占用可退额度")
    void ignoresNonSucceededRefunds() {
        long transactionId = insertTransaction();
        insertRefund(transactionId, new BigDecimal("10.0000"), new BigDecimal("10.0000"), "SUCCEEDED");
        insertRefund(transactionId, new BigDecimal("20.0000"), null, "PROCESSING");
        insertRefund(transactionId, new BigDecimal("30.0000"), null, "FAILED");

        assertThat(paymentRefundDao.sumSucceededByTransaction(transactionId))
                .as("只有成功退款进累计")
                .isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("成功态缺少渠道实退额会被数据库拒绝：累计口径无需回退")
    void succeededRefundWithoutProviderAmountIsRejectedBySchema() {
        long transactionId = insertTransaction();
        // ck_payment_refund_success_provider_amount（V101）：SUCCEEDED 必有 provider_amount。
        // 正因如此，SUM(provider_amount) 不会因 NULL 丢掉任何成功行 —— 累计口径无需 COALESCE 回退。
        assertThatThrownBy(() -> insertRefund(transactionId, new BigDecimal("12.0000"), null, "SUCCEEDED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("无成功退款时返回 0，而不是 NULL")
    void returnsZeroWhenNothingSucceeded() {
        long transactionId = insertTransaction();
        assertThat(paymentRefundDao.sumSucceededByTransaction(transactionId)).isEqualByComparingTo("0");
    }

    /** 造一条交易事实（append-only，仅作为退款的外键宿主）。 */
    private long insertTransaction() {
        String marker = UUID.randomUUID().toString().replace("-", "");
        // ck_payment_transaction_paid_pairing：SUCCEEDED 必须带 paid_at。
        return jdbc.queryForObject("""
                INSERT INTO xsy_v2.payment_transaction
                    (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                     paid_at, created_by)
                VALUES (?, 1, 'MOCK', ?, 1000.0000, 1000.0000, 'SUCCEEDED', ?, 'IT-PAY02')
                RETURNING id
                """, Long.class, "IT-PAY02-TXN-" + marker, "IT-PAY02-PTN-" + marker,
                java.time.OffsetDateTime.now());
    }

    /** 直接插入退款事实行，绕开服务层以确保测的是 SQL 口径本身。 */
    private void insertRefund(long transactionId, BigDecimal amount, BigDecimal providerAmount, String status) {
        jdbc.update("""
                INSERT INTO xsy_v2.payment_refund
                    (refund_no, intent_id, transaction_id, provider, amount, status, provider_amount,
                     refunded_at, created_by)
                VALUES (?, 1, ?, 'MOCK', ?, ?, ?, ?, 'IT-PAY02')
                """, "IT-PAY02-RFD-" + UUID.randomUUID().toString().replace("-", ""), transactionId,
                amount, status, providerAmount,
                "SUCCEEDED".equals(status) ? java.time.OffsetDateTime.now() : null);
    }
}
