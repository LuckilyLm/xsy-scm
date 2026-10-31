package com.xsy.scm.order.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import org.springframework.stereotype.Service;

/**
 * 销售订单、退货单、退款单统一使用 {@code XSY + 雪花 ID} 单号；单据类型由各自业务字段区分。
 */
@Service
public class OrderNumberGenerator {

    public String order() {
        return ScmDocumentNumbers.next();
    }

    public String returned() {
        return ScmDocumentNumbers.next();
    }

    public String refund() {
        return ScmDocumentNumbers.next();
    }
}
