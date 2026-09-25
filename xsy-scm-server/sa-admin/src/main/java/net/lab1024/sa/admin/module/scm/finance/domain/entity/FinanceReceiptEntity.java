package net.lab1024.sa.admin.module.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 收款：一笔钱一单，可无对应应收（预收，Q16）。
 *
 * <p><b>没有来源列</b>：收款一律人工登记，与应收的关系只通过 {@code finance_write_off} 表达
 * （Q16 允许无应收的预收）。<b>没有状态列</b>：待核销余额读时派生（Q17）。
 *
 * <p><b>纠错只能新增 {@code REVERSE} 行</b>（D-3）：登错金额、登错客户、登错凭据号都不能改原行。
 * 反向行的金额**同样恒为正**，方向由 {@code entryType} 表达；反向前该单的已用额必须为 0
 * （即先反向其全部核销），否则会出现「已用 &gt; 有效额」的负待核销余额。
 *
 * <p>{@code externalReference} 只是资金凭据文本，<b>不唯一、也不作幂等键</b>（设计稿 §5）：
 * 银行流水号跨客户重复是真实存在的，把它当唯一键会让第二笔合法收款登不进去。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("finance_receipt")
public class FinanceReceiptEntity extends FinanceRecord {

    private String receiptNo;

    /**
     * 收款对象恒为客户（预收也来自客户，Q16）；范围按 {@code customer.seller_id} 收窄（D-5）。
     */
    private Long customerId;

    private String customerNameSnapshot;

    /**
     * 一笔钱一单，恒 &gt; 0；反向行金额同样为正。
     */
    private BigDecimal amount;

    /**
     * {@code ScmFinancePaymentMethodEnum}：固定三值，与付款共用同一套枚举（Q21）。
     * 本期不含任何在线支付 / 余额 / 充值 / 货到付款方式（那属 P5）。
     */
    private String method;

    /**
     * 收款时点，登记人填写。
     */
    private OffsetDateTime receivedAt;

    /**
     * {@code ScmFinanceReverseEntryTypeEnum} 的 {@code NORMAL / REVERSE}（D-3）。
     */
    private String entryType;

    /**
     * 反向行必填，指向被冲的 {@code NORMAL} 收款；正常行必须为 {@code null}。
     * 一条 {@code NORMAL} 最多一条 {@code REVERSE}（{@code uk_finance_receipt_single_reverse}）。
     */
    private Long reverseOfId;

    /**
     * 反向原因，反向行必填非空。
     */
    private String reason;

    private String externalReference;

    private String remark;
}
