package com.xsy.scm.payment.domain.form;

import java.time.LocalDate;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/** 交易查询。 */
@Data
public class PaymentTransactionQueryForm extends PageParam {

    private Long intentId;

    private Long customerId;

    private String provider;

    private String status;

    /** 本地交易号 / 渠道交易号 / 业务单号的模糊匹配。 */
    private String keyword;
}
