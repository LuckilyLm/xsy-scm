package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 一笔付款单记录一次实际付款，可以没有对应应付单。订单退款付款须关联退款来源，退款审批完成不等于已经付款。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_payment")
public class FinancePaymentEntity extends FinanceRecord {

    private String paymentNo;

    /**
     * {@code ScmFinanceCounterpartyTypeEnum}：付给供应商（应付 / 预付）或客户（退款）。
     */
    private String counterpartyType;

    /**
     * 对方主键。{@code SUPPLIER} 侧不收窄范围（供应商主档按采购团队共享读取，无 supplier 维度）；{@code CUSTOMER} 侧按 {@code xsy_customer.seller_id} 收窄。
     */
    private Long counterpartyId;

    private String counterpartyNameSnapshot;

    /**
     * 恒 &gt; 0；退款付款必须等于 {@code xsy_order_refund.refund_amount}。反向行金额同样为正。
     */
    private BigDecimal amount;

    /**
     * {@code ScmFinancePaymentMethodEnum}，与收款共用同一套三值枚举。
     */
    private String method;

    private OffsetDateTime paidAt;

    /**
     * {@code ScmFinanceReverseEntryTypeEnum} 的 {@code NORMAL / REVERSE}。
     */
    private String entryType;

    /**
     * 反向行必填，指向被冲的 {@code NORMAL} 付款；正常行必须为 {@code null}。一条 {@code NORMAL} 最多一条
     * {@code REVERSE}（{@code uk_finance_payment_single_reverse}）。
     */
    private Long reverseOfId;

    /**
     * 反向原因，反向行必填非空。
     */
    private String reason;

    private String externalReference;

    /**
     * {@code ScmFinancePaymentSourceTypeEnum}；供应商付款无来源，客户付款使用 {@code ORDER_REFUND}。与 {@link #sourceId}
     * 要么都空要么都齐（{@code ck_finance_payment_source_pairing}）。
     */
    private String sourceType;

    /**
     * {@code xsy_order_refund.id}；反向行必须为 {@code null}。
     */
    private Long sourceId;

    private String remark;
}
