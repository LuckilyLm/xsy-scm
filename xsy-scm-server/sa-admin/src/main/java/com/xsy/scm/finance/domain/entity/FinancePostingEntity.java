package com.xsy.scm.finance.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 过账特殊单据：财务对外的虚拟记账留痕。
 *
 * <p>
 * 它不生成应收应付、不参与核销、不改客户余额与账龄；金额只在本表与操作日志里出现。 客户名称按登记时快照保存，客户后来改名不改历史过账。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_posting")
public class FinancePostingEntity extends FinanceRecord {

    private String postingNo;

    private Long customerId;

    private String customerNameSnapshot;

    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    /**
     * 过账业务日期，按纸质单据填写。
     */
    private LocalDate documentDate;

    /**
     * 过账金额，恒 &gt; 0。
     */
    private BigDecimal amount;

    /**
     * 税点百分比（0–100），按单据原样记录；不据此计算税额。
     */
    private BigDecimal taxRate;

    /**
     * {@code ScmFinancePostingRefundStatusEnum}：未返 / 部分返 / 已返。
     */
    private String refundStatus;

    private String remark;

    // 没有 status / voided_at 列：是否作废由 xsy_finance_posting_void 有没有对应行推导，
    // 与抹零「状态从核销行推导」同一取向，避免两份会漂移的真值。
}
