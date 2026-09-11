package com.xianshuyuan.scm.marketing.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.marketing.entity.PromotionScope;
import com.xianshuyuan.scm.marketing.entity.PromotionStatus;
import com.xianshuyuan.scm.marketing.entity.PromotionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 促销活动视图。effective 由服务端按当前时间与启用状态推导。
 */
public record PromotionResponse(Long id, String name, PromotionType type, PromotionScope scopeType,
                                JsonNode scopeIds, BigDecimal thresholdAmount, BigDecimal discountRate,
                                BigDecimal reduceAmount, BigDecimal promoPrice, Long giftSkuId,
                                BigDecimal giftQuantity, BigDecimal limitQuantity, OffsetDateTime startAt,
                                OffsetDateTime endAt, PromotionStatus status, Integer priority,
                                String description, boolean effective) {
}
