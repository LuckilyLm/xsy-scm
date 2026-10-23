package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 订单详情里的已冻结优惠（确认时由营销域落库的事实）。
 *
 * <p>
 * 客户看到的是与后台一致的冻结结果，不是重新试算： 确认之后活动或券再变，这单已成立的优惠也不变。
 */
@Data
public class MallOrderDiscountVO {

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountAmount;

    private String couponName;

    private List<String> activityNames = new ArrayList<>();
    private List<String> giftDescriptions = new ArrayList<>();
}
