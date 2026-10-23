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
 * 付款登记只新增正常付款记录。供应商付款和客户退款按对方类型、来源与金额分别验证；客户退款必须关联已完成的退款单。
 */
@Data
public class FinancePaymentAddForm {

    /**
     * {@code ScmFinanceCounterpartyTypeEnum} 两值之一。
     */
    @NotNull(message = "往来方类型不能为空")
    private String counterpartyType;

    /**
     * 对方主键：{@code SUPPLIER} 时为 {@code xsy_supplier.id}；{@code CUSTOMER} 时以 {@code xsy_order_refund.customer_id}
     * 为准，本字段只做一致性校验。
     */
    @NotNull(message = "往来方编号不能为空")
    private Long counterpartyId;

    /**
     * 付款金额：一律 JSON 字符串，整数最多 14 位、小数最多 4 位；服务端按 {@link ScmDecimalStrings} 统一成 scale 4，并要求严格大于 0。
     */
    @NotNull(message = "付款金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "付款金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    /**
     * 方式取 {@code ScmFinancePaymentMethodEnum} 三值，与收款共用同一套枚举。
     */
    @NotNull(message = "付款方式不能为空")
    private String method;

    /**
     * 实际付款业务时点由登记人填写。服务端不以 {@code now()} 代替，也不限制为当前时间以前。
     */
    @NotNull(message = "付款时间不能为空")
    private OffsetDateTime paidAt;

    /**
     * 资金凭据号，只是文本：可空、可重复、不建唯一约束。
     *
     * <p>
     * 不会从 {@code xsy_order_refund.external_reference} 自动带入：那一列属于订单域的退款记录，本列属于财务的实际资金动作，两者职责不同，必须由登记人单独提供。
     */
    @Size(max = 128, message = "资金凭据号长度不能超过128")
    private String externalReference;

    /**
     * 客户侧必须为 {@code ORDER_REFUND}； {@code SUPPLIER} 侧必须为空（与 {@link #sourceId}
     * 成对，{@code ck_finance_payment_source_pairing}）。
     */
    private String sourceType;

    /**
     * {@code xsy_order_refund.id}；与 {@link #sourceType} 同空同非空。
     */
    private Long sourceId;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
