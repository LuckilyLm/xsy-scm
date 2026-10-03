package com.xsy.scm.payment.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.payment.dao.PaymentDocumentNumberDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 支付域单号生成。
 *
 * <pre>
 * 支付意图：PYI + yyyyMMdd + 至少 6 位
 * 交易：    PYT + yyyyMMdd + 至少 6 位
 * 退款：    PYR + yyyyMMdd + 至少 6 位
 * 对账：    PYC + yyyyMMdd + 至少 6 位
 * </pre>
 *
 * <p>
 * 前缀刻意不用 {@code PAY}：那个值已被 Finance 的 {@code ScmFinanceOperationTypeEnum.PAY} 声明，
 * 支付单号与财务操作类型是两个不同的命名空间，复用同一个字符串只会让质量门禁与读者都产生歧义。
 *
 * <p>
 * 共用一条序列：四类单号从同一个池里取，因此**全局不会撞号**，也不需要四张计数器表。
 * 代价是某一类单号的数字不连续 —— 那本来就不该被当作业务信息。
 *
 * <p>
 * 必须在事务内调用（{@code nextval} 不回滚，跳号可接受）。
 */
@Service
@RequiredArgsConstructor
public class PaymentNumberGenerator {

    public static final String INTENT_PREFIX = "PYI";
    public static final String TRANSACTION_PREFIX = "PYT";
    public static final String REFUND_PREFIX = "PYR";
    public static final String RECONCILIATION_PREFIX = "PYC";

    private final PaymentDocumentNumberDao paymentDocumentNumberDao;

    public String nextIntentNo() {
        return format(INTENT_PREFIX, paymentDocumentNumberDao.nextDocumentNo());
    }

    public String nextTransactionNo() {
        return format(TRANSACTION_PREFIX, paymentDocumentNumberDao.nextDocumentNo());
    }

    public String nextRefundNo() {
        return format(REFUND_PREFIX, paymentDocumentNumberDao.nextDocumentNo());
    }

    public String nextReconciliationNo() {
        return format(RECONCILIATION_PREFIX, paymentDocumentNumberDao.nextDocumentNo());
    }

    /** 单号拼接的纯函数（单测可直接覆盖，不需要 DB）。 */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
