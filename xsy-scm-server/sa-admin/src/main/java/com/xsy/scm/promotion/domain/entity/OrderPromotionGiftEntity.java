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
 * 满赠赠品权益（冻结事实）。
 *
 * <p>
 * 赠品是<b>非金额权益</b>：它不让订单金额变小，因此不参与优惠分摊、也不进 {@code order_discount}。 但它必须可追溯 —— 「这单因为哪个活动、拿到什么赠品、多少」，出库、分拣、小票与成本归集都读这一行，
 * 不在各自环节重算活动规则。
 *
 * <p>
 * 快照不可变（表上有触发器）：能改等于发货时可以换赠品。
 */
@Data
@TableName(value = "order_promotion_gift", autoResultMap = true)
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

    /** 活动规则原文（受控键值），供解释「满多少赠多少」。 */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> ruleSnapshot;

    @Version
    private Integer version = 0;

    private OffsetDateTime createdAt;

    private String createdBy;
}
