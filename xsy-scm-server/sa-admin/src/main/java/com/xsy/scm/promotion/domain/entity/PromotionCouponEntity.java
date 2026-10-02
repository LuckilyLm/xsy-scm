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

/**
 * 优惠券模板。
 *
 * <p>
 * {@code discountValue} 的含义由 {@code discountType} 决定：{@code AMOUNT} 是减免金额，
 * {@code RATE} 是折扣率（0.95 = 95 折）。两者不合并成一列，因为「减 5 元」和「95 折」
 * 在展示与对账上完全不同，用一列加一个类型标记是唯一不会读错的表达。
 */
@Data
@TableName(value = "promotion_coupon", autoResultMap = true)
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
