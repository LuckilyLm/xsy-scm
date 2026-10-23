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
 * 满赠赠品单独记录赠品、活动和数量，不参与金额优惠分摊。赠品信息在订单确认时保存，分拣、出库和打印读取同一份数据。
 */
@Data
@TableName(value = "xsy_order_promotion_gift", autoResultMap = true)
public class OrderPromotionGiftEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long salesOrderId;

    private Long activityId;

    private Integer activityVersion;

    private Long skuId;

    private String skuCodeSnapshot;

    private String productNameSnapshot;

    private String specNameSnapshot;

    private String saleUnitSnapshot;

    /** 赠品数量，恒 &gt; 0。 */
    private BigDecimal quantity;

    /** 活动规则原文（受控键值），用于解释「满多少赠多少」。 */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> ruleSnapshot;

    @Version
    private Integer version = 0;

    private OffsetDateTime createdAt;

    private String createdBy;
}
