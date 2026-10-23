package com.xsy.scm.balance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 余额流水只能追加，不能修改历史记录。金额始终为正，由收入或支出方向决定加减。
 */
@Data
@TableName("xsy_customer_balance_movement")
public class CustomerBalanceMovementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String movementNo;

    private Long accountId;

    /** 余额账户所有者（结算主体）。冗余一份，按结算主体查流水不必回表。 */
    private Long settlementCustomerId;

    /** 本次业务实际发生的客户；集团场景下与余额账户所有者不同，用于追溯。 */
    private Long customerId;

    /** {@code RECHARGE} / {@code CONSUME} / {@code REFUND} / {@code CORRECTION}。 */
    private String type;

    /** {@code CREDIT} / {@code DEBIT}。 */
    private String direction;

    private BigDecimal amount;

    private String sourceType;

    private Long sourceId;

    /** 人工更正必填（数据库有 CHECK）。 */
    private String reason;

    private OffsetDateTime occurredAt;

    @Version
    private Integer version = 0;

    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
