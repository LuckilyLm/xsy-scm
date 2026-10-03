package com.xsy.scm.payment.domain.form;

import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/** 退款查询。 */
@Data
public class PaymentRefundQueryForm extends PageParam {

    private Long transactionId;

    private Long intentId;

    private String provider;

    private String status;

    private String sourceType;

    private Long sourceId;

    /** 退款单号 / 渠道退款号的模糊匹配。 */
    private String keyword;
}
