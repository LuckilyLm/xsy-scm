package com.xsy.scm.mall.cart.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 所选商品的预估结果。金额一律由服务端按四位小数定点计算，客户端只展示、不累计。
 */
@Data
public class MallCartEstimateVO {
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal estimatedAmount = BigDecimal.ZERO;
    /**
     * 所选商品中可结算的行数
     */
    private Integer availableCount = 0;
    /**
     * 所选商品行数（含不可结算）
     */
    private Integer selectedCount = 0;
    /**
     * 所选商品中当前不可结算的 SKU，供前端提示
     */
    private List<Long> unavailableSkuIds = new ArrayList<>();
}
