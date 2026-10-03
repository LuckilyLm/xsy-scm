package com.xsy.scm.payment.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建支付意图。
 *
 * <p>
 * <b>金额必须显式给出</b>：这里没有「按订单金额自动算」的入口。一张订单可以只收一部分、
 * 也可以拆成余额 + 在线支付两条意图，应付多少是业务决定，不是能从订单推出来的。
 */
@Data
public class PaymentIntentCreateForm {

    @NotNull(message = "客户不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    /** 业务来源，当前只支持 {@code SALES_ORDER}。 */
    @NotBlank(message = "业务来源不能为空")
    private String sourceType;

    @NotNull(message = "业务单据不能为空")
    @Positive(message = "业务单据 ID 必须大于0")
    private Long sourceId;

    @NotNull(message = "应付金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "应付金额必须大于0")
    @Digits(integer = 14, fraction = 4, message = "应付金额最多14位整数和4位小数")
    private BigDecimal amount;

    /** {@code ONLINE} / {@code BALANCE}。 */
    @NotBlank(message = "支付方式不能为空")
    private String method;

    /** {@code MOCK} / {@code WECHAT}。 */
    @NotBlank(message = "支付渠道不能为空")
    private String provider;

    /**
     * 仅本地模拟渠道可指定回放剧本（{@code SUCCESS} / {@code FAILURE} / {@code DELAYED} / {@code EXPIRED}）。
     * 真实渠道带上它一律拒收。
     */
    private String mockScenario;

    private String remark;
}
