package com.xsy.scm.balance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 只定位已完成售后退款单，金额与钱包归属由原事实确定。 */
@Data
public class BalanceRefundForm {
    @NotNull(message = "退款单不能为空")
    @Positive(message = "退款单编号必须大于0")
    private Long refundId;
}
