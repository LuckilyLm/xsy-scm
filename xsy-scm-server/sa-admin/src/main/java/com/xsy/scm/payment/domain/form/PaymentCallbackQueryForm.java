package com.xsy.scm.payment.domain.form;

import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/** 回调记录查询。 */
@Data
public class PaymentCallbackQueryForm extends PageParam {

    private String provider;

    /** {@code RECEIVED} / {@code APPLIED} / {@code DUPLICATE} / {@code REJECTED}。 */
    private String processStatus;

    private Long transactionId;

    /** 渠道事件 id 的模糊匹配。 */
    private String keyword;
}
