package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 核销关系行：一笔收 / 付款对一笔应收 / 应付的一次分配（M:N）。
 *
 * <p><b>刻意不存 {@code settled_amount} / {@code open_amount} / {@code status}</b>：
 * 已核销额、未核销额、超额核销与结清状态全部读时派生（全局不变量 6）。落库就等于给
 * 同一个事实造第二个权威来源，而派生态一旦落库就会漂移 —— 与库存预警状态「读时计算、不落库」
 * 同一范式。
 *
 * <p><b>刻意不冗余对方列</b>（不存 {@code customerId} / {@code supplierId}）：
 * 「收款只核该客户的应收」由服务层在持有锁之后读 source 与 target 行比对，
 * 冗余一份就会让同一事实出现两个可能不一致的来源。
 *
 * <p><b>撤销 = 新增 {@code REVERSE} 行</b>：一条 {@code NORMAL} 最多被反向一次
 * （{@code uk_finance_write_off_single_reverse}），重复撤销在库级失败，不靠服务层先查后判。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("finance_write_off")
public class FinanceWriteOffEntity extends FinanceRecord {

    private String writeOffNo;

    /**
     * {@code ScmFinanceWriteOffSourceTypeEnum}：钱从哪来。
     */
    private String sourceType;

    /**
     * {@code finance_receipt.id} 或 {@code finance_payment.id}。
     */
    private Long sourceId;

    /**
     * {@code ScmFinanceWriteOffTargetTypeEnum}：抵到哪去。数据范围随本列。
     */
    private String targetType;

    /**
     * {@code finance_receivable.id} 或 {@code finance_payable.id}。
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
     * 核销时点。核销额按本列取窗，与应收发生额按 {@code event_at} 取窗
     * 是**两个不同的时间轴**，混用会算出既非流量也非存量的数字。
     */
    private OffsetDateTime writtenOffAt;

    /**
     * 操作人（loginId 形态，与审计列同口径）。
     */
    private String operator;
}
