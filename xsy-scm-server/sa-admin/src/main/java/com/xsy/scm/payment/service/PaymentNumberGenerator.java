package com.xsy.scm.payment.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import org.springframework.stereotype.Service;

/**
 * 支付意图、交易、退款和回调统一使用 {@code XSY + 雪花 ID} 单号；单据类型由各自业务字段区分。
 */
@Service
public class PaymentNumberGenerator {

    public String nextIntentNo() {
        return ScmDocumentNumbers.next();
    }

    public String nextTransactionNo() {
        return ScmDocumentNumbers.next();
    }

    public String nextRefundNo() {
        return ScmDocumentNumbers.next();
    }

    public String nextReconciliationNo() {
        return ScmDocumentNumbers.next();
    }
}
