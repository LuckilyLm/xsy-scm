package com.xsy.scm.finance.domain.entity;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 过账作废事实：谁在什么时候因为什么让一张过账失效。
 *
 * <p>
 * 单独成表而不是在过账行上写 {@code voided_at}：作废是独立事件， 一份事实一份行，重复作废由 {@code posting_id} 唯一索引直接挡住。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_posting_void")
public class FinancePostingVoidEntity extends FinanceRecord {

    private Long postingId;

    private String reason;

    private OffsetDateTime voidedAt;
}
