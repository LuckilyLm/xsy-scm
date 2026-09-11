package com.xianshuyuan.scm.marketing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 新建 / 修改促销活动请求。type 与 scopeType 必须为对应枚举值，由服务端校验。
 */
public record PromotionSaveRequest(
        @NotBlank(message = "活动名称不能为空") @Size(max = 100, message = "活动名称过长") String name,
        @NotBlank(message = "活动类型不能为空") @Size(max = 16) String type,
        @Size(max = 16) String scopeType,
        JsonNode scopeIds,
        @PositiveOrZero(message = "门槛金额不能为负") BigDecimal thresholdAmount,
        @PositiveOrZero(message = "折扣率不能为负") BigDecimal discountRate,
        @PositiveOrZero(message = "减免金额不能为负") BigDecimal reduceAmount,
        @PositiveOrZero(message = "活动价不能为负") BigDecimal promoPrice,
        Long giftSkuId,
        @PositiveOrZero(message = "赠品数量不能为负") BigDecimal giftQuantity,
        @PositiveOrZero(message = "限购数量不能为负") BigDecimal limitQuantity,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        Integer priority,
        @Size(max = 500, message = "活动说明过长") String description,
        @Size(max = 16) String status
) {
}
