package com.xsy.scm.promotion.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 订单优惠冻结。
 *
 * <p>
 * 一个订单至多一份（唯一约束）。活动快照与券快照一起冻结：退款按原分摊反向，不用退款时的
 * 当前活动重算 —— 否则「双十一买的单」在双十二退款会得到另一个优惠金额。
 *
 * <p>
 * 表上有触发器拒绝改动金额、分摊与快照。
 */
@Data
@TableName(value = "order_discount", autoResultMap = true)
public class OrderDiscountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long salesOrderId;

    private Long activityId;

    private Integer activityVersion;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> activitySnapshot;

    private Long couponInstanceId;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> couponSnapshot;

    private BigDecimal baseAmount;

    private BigDecimal discountAmount;

    /** 逐行分摊：{@code [{orderItemId, baseAmount, discountAmount}]}。 */
    private String allocations;

    /** 承接舍入差额的行；没有差额时为 {@code null}。 */
    private Long roundingTargetItemId;

    private OffsetDateTime createdAt;

    private String createdBy;

    @Version
    private Integer version = 0;
}
