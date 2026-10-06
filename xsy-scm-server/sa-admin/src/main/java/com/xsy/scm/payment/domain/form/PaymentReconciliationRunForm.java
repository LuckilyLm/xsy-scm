package com.xsy.scm.payment.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/**
 * 执行对账。
 *
 * <p>
 * 业务日由调用方显式给出，<b>不默认取「今天」</b>：对账通常在次日甚至更晚执行， 默认今天会让「对哪一天」变成一个隐式假设。
 */
@Data
public class PaymentReconciliationRunForm {

    @NotBlank(message = "支付渠道不能为空")
    private String provider;

    @NotNull(message = "业务日不能为空")
    private LocalDate bizDate;
}
