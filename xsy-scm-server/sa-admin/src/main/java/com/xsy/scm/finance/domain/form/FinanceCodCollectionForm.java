package com.xsy.scm.finance.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 货到付款收款确认请求。
 *
 * <p>
 * 只带订单、实收金额、资金形式、业务时点与凭据号：客户与冻结结算主体取订单的正常应收，核销目标也由该应收决定，客户端不能指定。
 */
@Data
public class FinanceCodCollectionForm {

    @NotNull(message = "订单不能为空")
    @Positive(message = "订单不合法")
    private Long orderId;

    /**
     * 实收金额：一律 JSON 字符串，服务端统一保留 4 位小数并要求大于 0。
     *
     * <p>
     * 少收按实收登记（应收剩余仍未结），多收由核销命令按「不得超过可核销余额」拒绝。
     */
    @NotNull(message = "实收金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "实收金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    /**
     * 实际资金形式，取收款方式的允许值（CASH / BANK_TRANSFER 等）。
     *
     * <p>
     * 不含 COD：货到付款是结算时机，不是资金形式；实收方式仍填现金或转账，见 docs/decisions.md 的商城结算规则。
     */
    @NotNull(message = "收款方式不能为空")
    private String method;

    /**
     * 实际收款业务时点，由登记人填写；服务端不以 {@code now()} 代替。
     */
    @NotNull(message = "收款时间不能为空")
    private OffsetDateTime receivedAt;

    /**
     * 资金凭据号（银行流水号等），只作文本，不参与幂等。
     */
    @Size(max = 128, message = "资金凭据号长度不能超过128")
    private String externalReference;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
