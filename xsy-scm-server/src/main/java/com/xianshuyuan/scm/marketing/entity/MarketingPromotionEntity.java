package com.xianshuyuan.scm.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 促销活动。抢购 / 满减 / 满赠 / 限时特价共用一张表，按 type 区分生效字段：
 * 满减=threshold_amount+reduce_amount，满赠=threshold_amount+gift，
 * 限时特价与抢购=promo_price(+limit_quantity)。status / type / scopeType 以字符串落库。
 */
@Data
@TableName(value = "marketing_promotion", autoResultMap = true)
public class MarketingPromotionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String scopeType;
    @TableField(typeHandler = JsonbJsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode scopeIds;
    private BigDecimal thresholdAmount;
    private BigDecimal discountRate;
    private BigDecimal reduceAmount;
    private BigDecimal promoPrice;
    private Long giftSkuId;
    private BigDecimal giftQuantity;
    private BigDecimal limitQuantity;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private String status;
    private Integer priority;
    private String description;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
