package com.xsy.scm.promotion.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 客户持有的券。
 *
 * <p>
 * 状态机：{@code AVAILABLE → RESERVED → USED}，{@code RESERVED → RELEASED → AVAILABLE}（重发见下）。
 * <b>预览不占用</b>（ADR-009：结算预览不等于最终占用）；只有确认下单才 {@code RESERVED}。
 *
 * <p>
 * 释放后不自动回到 {@code AVAILABLE}：{@code RELEASED} 是「这张券已经被释放过」的历史痕迹，
 * 直接改回 AVAILABLE 会让「同一张券被占用过几次」无从追溯。要复用就重新发一张。
 */
@Data
@TableName(value = "promotion_coupon_instance", autoResultMap = true)
public class PromotionCouponInstanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long couponId;

    private Long customerId;

    private String instanceNo;

    /** {@code AVAILABLE} / {@code RESERVED} / {@code USED} / {@code RELEASED}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reservedOrderId;

    private OffsetDateTime reservedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long usedOrderId;

    private OffsetDateTime usedAt;

    private OffsetDateTime releasedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String releaseReason;

    @Version
    private Integer version = 0;
}
