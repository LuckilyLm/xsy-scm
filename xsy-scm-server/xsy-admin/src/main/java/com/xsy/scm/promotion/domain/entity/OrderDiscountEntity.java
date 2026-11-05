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
 * 订单优惠。一个订单至多一份（唯一约束）。
 *
 * <p>
 * 活动与券的内容在订单确认时一起保存：退款按原分摊反向处理，不用退款时的当前活动重算， 否则「双十一买的单」在双十二退款会得到另一个优惠金额。表上有触发器拒绝改动金额、分摊和保存的内容。
 */
@Data
@TableName(value = "xsy_order_discount", autoResultMap = true)
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

    /** 限时特价让利已包含在总优惠和明细分配中，单独保存只为统计；不能大于订单总优惠。 */
    private BigDecimal specialDiscountAmount;

    /** 逐行分摊：{@code [{orderItemId, baseAmount, discountAmount}]}。 */
    private String allocations;

    /** 承接舍入差额的行；没有差额时为 {@code null}。 */
    private Long roundingTargetItemId;

    private OffsetDateTime createdAt;

    private String createdBy;

    @Version
    private Integer version = 0;
}
