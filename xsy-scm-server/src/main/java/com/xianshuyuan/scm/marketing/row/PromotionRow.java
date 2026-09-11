package com.xianshuyuan.scm.marketing.row;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 促销活动查询行。仅用于 SQL 映射，与实体字段一致，因含 JSONB 列需显式 typeHandler。
 */
@Data
public class PromotionRow {

    private Long id;
    private String name;
    private String type;
    private String scopeType;
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
}
