package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class MallOrderItemVO {
    private Long itemId;
    private Long skuId;
    private String productName;
    private String specName;
    private String saleUnit;
    private String productType;
    /**
     * 是否按实重结算
     */
    private Boolean nonStandard;
    private String primaryImageUrl;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;
    /**
     * 实重/实收数量；未分拣时为 null，客户端展示「待称重」
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    /**
     * 下单行金额（下单量 × 锁定单价）
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedLineAmount;
    /**
     * 结算行金额（实收量 × 锁定单价）；订单未确认时为 null
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementLineAmount;
}
