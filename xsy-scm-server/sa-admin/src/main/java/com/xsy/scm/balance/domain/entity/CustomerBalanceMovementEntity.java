package com.xsy.scm.balance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 客户余额流水（append-only）。
 *
 * <p>
 * 金额<b>恒正</b>，方向由 {@link #direction} 独立表达：与 Finance 的「数量恒正、方向编码在类型里」 同一套风格，避免「正负号 + 方向」两处表达同一件事。
 *
 * <p>
 * 改余额靠追加反向流水，绝不修改历史行（库上有 append-only CHECK）。
 */
@Data
@TableName("customer_balance_movement")
public class CustomerBalanceMovementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String movementNo;

    private Long accountId;

    /** 钱包所有者（结算主体）。冗余存一份：按结算主体查流水不必回表。 */
    private Long settlementCustomerId;

    /** 本次业务实际发生的客户；集团场景下与钱包所有者不同，用于追溯。 */
    private Long customerId;

    /** {@code RECHARGE} / {@code CONSUME} / {@code REFUND} / {@code CORRECTION}。 */
    private String type;

    /** {@code CREDIT} / {@code DEBIT}。 */
    private String direction;

    private BigDecimal amount;

    private String sourceType;

    private Long sourceId;

    /** 人工更正必填（库上有 CHECK）。 */
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
