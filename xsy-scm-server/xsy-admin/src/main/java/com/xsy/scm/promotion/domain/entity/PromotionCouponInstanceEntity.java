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
 * 优惠券预览不占用，确认订单才占用。释放后保留历史释放状态，不能直接改回可用；再次发券应创建新实例。
 */
@Data
@TableName(value = "xsy_promotion_coupon_instance", autoResultMap = true)
public class PromotionCouponInstanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long couponId;

    private Long customerId;

    private String instanceNo;

    private String issueSource;

    /**
     * {@code AVAILABLE} / {@code RESERVED} / {@code USED} / {@code RELEASED}。
     */
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
