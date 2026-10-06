package com.xsy.scm.finance.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

import java.time.OffsetDateTime;

/**
 * 收款登记请求，只创建 {@code NORMAL} 收款事实。
 *
 * <p>
 * 收款登记只携带客户、金额、方式、业务时点、资金凭据号和备注；核销、审批、附件、币种、账期与余额不属于这条命令。
 */
@Data
public class FinanceReceiptAddForm {

    @NotNull(message = "客户不能为空")
    private Long customerId;

    /**
     * 收款金额：一律 JSON 字符串，整数最多 14 位、小数最多 4 位；服务端按 {@link ScmDecimalStrings} 统一成 scale 4，并要求严格大于 0。
     */
    @NotNull(message = "收款金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "收款金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    /**
     * 方式取 {@code ScmFinancePaymentMethodEnum} 的 CASH、BANK_TRANSFER、OTHER 三值。
     */
    @NotNull(message = "收款方式不能为空")
    private String method;

    /**
     * 实际收款业务时点由登记人填写。服务端不以 {@code now()} 代替，因为业务时点不同于落库时刻。
     */
    @NotNull(message = "收款时间不能为空")
    private OffsetDateTime receivedAt;

    /**
     * 资金凭据号（银行流水号等），只是文本：允许为空、允许重复，既不参与幂等也不建唯一约束。 NORMAL 收款是人工登记的资金事实，本表<b>没有</b> {@code source_type/source_id}，
     * 因此不存在业务来源唯一索引：重复请求防护只有请求级 {@code Idempotency-Key} 一层， {@code uk_finance_receipt_no} 只是单据号唯一、不是业务事实幂等键。
     * 金额、凭据号、时点全部相同的两笔真实收款，只要来自两条命令就都应当成立。
     */
    @Size(max = 128, message = "资金凭据号长度不能超过128")
    private String externalReference;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
