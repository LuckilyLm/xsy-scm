package com.xsy.scm.balance.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

/** 发起在线充值。 */
@Data
public class BalanceRechargeCreateForm {

    /** 实际发起充值的客户；钱包取它的<b>结算主体</b>（集团下属单位充进集团钱包）。 */
    @NotNull(message = "客户不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "充值金额必须大于0")
    @Digits(integer = 14, fraction = 4, message = "充值金额最多14位整数和4位小数")
    private BigDecimal amount;

    /** 支付渠道（当前 MOCK）。 */
    @NotBlank(message = "支付渠道不能为空")
    private String provider;

    /** 仅本地模拟渠道可指定回放剧本。 */
    private String mockScenario;

    private String remark;
}
