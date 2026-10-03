package com.xsy.scm.payment.domain.form;

import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/** 支付意图查询。 */
@Data
public class PaymentIntentQueryForm extends PageParam {

    private Long customerId;

    /** 按业务单据（订单）收窄。 */
    private Long sourceId;

    private String provider;

    private String method;

    private String status;

    /** 意图号 / 业务单号 / 客户名的模糊匹配。 */
    private String keyword;
}
