package com.xsy.scm.finance.domain.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 抹零事实：对账时主动放弃的零头，冲减应收。
 *
 * <p>
 * 金额本身不参与余额计算——真正的冲减是 {@code xsy_finance_write_off} 里 {@code source_type = 'WAIVER'} 的那一行，本表只负责证明「这几分钱是主动免掉的，不是漏收」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_waiver")
public class FinanceWaiverEntity extends FinanceRecord {

    private String waiverNo;

    /**
     * 被抹零的应收单；只允许 {@code NORMAL} 应收。
     */
    private Long receivableId;

    private Long customerId;

    private Long settlementCustomerId;

    /**
     * 抹零金额，恒 &gt; 0；不允许用它代替收款。
     */
    private BigDecimal amount;

    private String reason;

    // 没有 status / reversed_at 等列：是否已反向由该抹零的核销行是否被 REVERSE 推导，
    // 读时看 xsy_finance_write_off，避免一份会漂移的派生态。
}
