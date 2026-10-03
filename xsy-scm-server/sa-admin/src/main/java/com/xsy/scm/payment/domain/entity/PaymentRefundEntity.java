package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 渠道退款事实。
 *
 * <p>
 * 与订单域的 {@code order_refund}（售后退款单）是两件事：{@code order_refund} 说「该退多少」，
 * 这里说「渠道实际退了多少、退到哪一笔交易上」。通过 {@code source_type = ORDER_REFUND}
 * + {@code source_id} 关联，且**一张售后退款单只对应一笔渠道退款**（唯一索引）。
 */
@Data
@TableName("payment_refund")
public class PaymentRefundEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private Long intentId;

    private Long transactionId;

    private String provider;

    /** **申请**退款金额（本地决定）。 */
    private BigDecimal amount;

    /**
     * **渠道实际退款金额**。成功态必有值（库上有 CHECK）。
     *
     * <p>
     * 与 {@link #amount} 分开：申请 100 而渠道实际退了 98 是可能发生的，
     * 3-11b 的资金反向事实认的是这一列，不是申请额。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal providerAmount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceId;

    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String providerRefundNo;

    /** 仅 MOCK：回放剧本（成功 / 失败）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mockScenario;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;

    private OffsetDateTime refundedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failureCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failureMessage;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
