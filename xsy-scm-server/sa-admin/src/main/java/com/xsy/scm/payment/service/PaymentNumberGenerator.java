package com.xsy.scm.payment.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.payment.dao.PaymentDocumentNumberDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 支付意图、交易、退款和回调使用各自的单号前缀及日期序号。
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

    /**
     * 单号拼接的纯函数（单测可直接覆盖，不需要 DB）。
     */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
