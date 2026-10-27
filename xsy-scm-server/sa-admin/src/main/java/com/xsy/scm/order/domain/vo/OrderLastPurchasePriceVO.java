package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 录单参考的最近采购价：只读快照，不参与当前商品定价，也不写回订单。
 */
@Data
public class OrderLastPurchasePriceVO {

    private Long skuId;

    /**
     * 最近一张已提交采购单上的采购单价（采购单位口径）。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchasePrice;

    /** 采购价对应的单位；录单页与销售单位不同时只作参考，不能直接比较。 */
    private String purchaseUnit;

    private String purchaseOrderNo;

    private OffsetDateTime submittedAt;

    private String supplierName;
}
