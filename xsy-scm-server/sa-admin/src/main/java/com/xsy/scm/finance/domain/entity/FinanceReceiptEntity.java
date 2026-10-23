package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 每笔实收款独立记账，可以暂不关联应收单。与应收的抵扣关系通过核销表保存，收款记录本身不可修改；系统收款与人工登记须保留来源区分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_receipt")
public class FinanceReceiptEntity extends FinanceRecord {

    private String receiptNo;

    /**
     * 收款对象恒为客户（预收也来自客户）；范围按 {@code xsy_customer.seller_id} 收窄。
     */
    private Long customerId;

    private String customerNameSnapshot;

    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    /**
     * 一笔钱一单，恒 &gt; 0；反向行金额同样为正。
     */
    private BigDecimal amount;

    /**
     * {@code ScmFinancePaymentMethodEnum}：固定三值，与付款共用同一套枚举。
     */
    private String method;

    /**
     * 收款时点，登记人填写。
     */
    private OffsetDateTime receivedAt;

    /**
     * {@code ScmFinanceReverseEntryTypeEnum} 的 {@code NORMAL / REVERSE}。
     */
    private String entryType;

    /**
     * 反向行必填，指向被冲的 {@code NORMAL} 收款；正常行必须为 {@code null}。一条 {@code NORMAL} 最多一条
     * {@code REVERSE}（{@code uk_finance_receipt_single_reverse}）。
     */
    private Long reverseOfId;

    /**
     * 反向原因，反向行必填非空。
     */
    private String reason;

    private String externalReference;

    private String remark;

    /**
     * 系统来源类型；人工登记的收款为空。
     *
     * <p>
     * 与 {@link #sourceId} 成对（数据库有 CHECK），且只出现在 {@code NORMAL} 行上；唯一索引 {@code uk_finance_receipt_source_active}
     * 保证同一来源只有一条正常收款记录。
     */
    private String sourceType;

    /**
     * 系统来源主键；支付交易取交易 id，分笔 COD 取幂等命令记录 id。
     */
    private Long sourceId;

    /**
     * 分笔 COD 收款所属订单；历史行仍以 source_id 表示订单。
     */
    private Long codOrderId;
}
