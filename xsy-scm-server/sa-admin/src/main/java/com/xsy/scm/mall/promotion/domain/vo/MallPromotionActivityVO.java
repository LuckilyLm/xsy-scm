package com.xsy.scm.mall.promotion.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 商城首页展示的进行中的活动。
 *
 * <p>
 * {@code rule} 是受控键值（已由营销域校验器收窄），商城只展示、不解释计算； 优惠如何作用在订单上由 {@code PromotionDiscountService} 统一计算。
 */
@Data
public class MallPromotionActivityVO {

    private Long id;

    private String activityName;

    private String activityType;

    /**
     * 类型展示名（服务端填，客户端不硬编码字典）。
     */
    private String activityTypeLabel;
    private String summary;
    private MallProductVO product;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal specialPrice;

    private OffsetDateTime validFrom;

    private OffsetDateTime validTo;

    @JsonIgnore
    private Map<String, Object> rule;
}
