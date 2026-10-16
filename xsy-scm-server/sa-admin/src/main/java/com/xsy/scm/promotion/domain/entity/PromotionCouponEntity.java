package com.xsy.scm.promotion.domain.entity;

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

/** 优惠券金额或折扣率由类型字段确定：固定金额直接减免，比例折扣按百分比计算，不能混淆。 */
@Data
@TableName(value = "xsy_promotion_coupon", autoResultMap = true)
public class PromotionCouponEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String couponCode;

    private String couponName;

    /** {@code AMOUNT} / {@code RATE}。 */
    private String discountType;

    private BigDecimal discountValue;

    private BigDecimal minOrderAmount;

    private OffsetDateTime validFrom;

    private OffsetDateTime validTo;

    /** {@code DRAFT} / {@code ACTIVE} / {@code STOPPED}。 */
    private String status;

    /** 是否开放商城客户自助领取；后台直接发券不受此标记约束。 */
    private Boolean claimable = false;

    /** 每个客户经商城入口可领取的上限；{@code null} 表示不限。 */
    private Integer perCustomerLimit;

    /** 经商城入口可领取的总量上限；{@code null} 表示不限。 */
    private Integer totalLimit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
