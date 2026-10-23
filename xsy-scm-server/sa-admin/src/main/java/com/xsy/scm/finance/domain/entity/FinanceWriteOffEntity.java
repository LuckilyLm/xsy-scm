package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 每条核销记录连接一笔收付款与一笔应收应付，未结余额从核销明细计算，不在关系行中重复保存。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_write_off")
public class FinanceWriteOffEntity extends FinanceRecord {

    private String writeOffNo;

    /**
     * {@code ScmFinanceWriteOffSourceTypeEnum}：钱从哪来。
     */
    private String sourceType;

    /**
     * {@code xsy_finance_receipt.id} 或 {@code xsy_finance_payment.id}。
     */
    private Long sourceId;

    /**
     * {@code ScmFinanceWriteOffTargetTypeEnum}：抵到哪去。数据范围随本列。
     */
    private String targetType;

    /**
     * {@code xsy_finance_receivable.id} 或 {@code xsy_finance_payable.id}。
     */
    private Long targetId;

    /**
     * 本次核销金额，恒 &gt; 0。已核销额 = {@code Σ NORMAL − Σ REVERSE}，读时算。
     */
    private BigDecimal amount;

    /**
     * {@code ScmFinanceReverseEntryTypeEnum} 的 {@code NORMAL / REVERSE}。
     */
    private String entryType;

    /**
     * 反向行必填，指向被冲的 {@code NORMAL} 核销；正常行必须为 {@code null}。
     */
    private Long reverseOfId;

    /**
     * 撤销原因，反向行必填非空。
     */
    private String reason;

    /**
     * 核销时点。核销额按本列取窗，与应收发生额按 {@code event_at} 取窗是<b>两个不同的时间轴</b>，混用会算出既非流量也非存量的数字。
     */
    private OffsetDateTime writtenOffAt;

    /**
     * 操作人（loginId 形态，与审计列同口径）。
     */
    private String operator;
}
