package com.xsy.scm.payment.domain.form;

import java.time.LocalDate;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/** 对账查询。 */
@Data
public class PaymentReconciliationQueryForm extends PageParam {

    private String provider;

    private String status;

    private LocalDate bizDateFrom;

    private LocalDate bizDateTo;
}
