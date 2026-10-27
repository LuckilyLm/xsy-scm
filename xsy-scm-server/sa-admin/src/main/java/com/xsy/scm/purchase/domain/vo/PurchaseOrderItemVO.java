package com.xsy.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购明细的剩余量和超收量按当前已收数量计算，不另存冗余结果；订单明细和收货对账保持一致。
 */
@Data
public class PurchaseOrderItemVO {
    private Long id;
    private Long skuId;
    private String spuCode;
    private String productName;
    private String skuCode;
    private String skuName;
    private java.util.Map<String, Object> specValues;
    private String purchaseUnit;
    private String productType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal remainingQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal overReceiptQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchasePrice;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal lineAmount;
    /** 待定价：TRUE 时 purchasePrice 为空、行金额按 0 计，页面必须显式标注。 */
    private Boolean pricePending;
    /** 价格窗口起点（采购单提交时刻）；为空表示尚未提交。 */
    private java.time.OffsetDateTime priceWindowStartedAt;
    private java.time.OffsetDateTime priceFilledAt;
    private Integer sortOrder;
    private Integer version;
    private List<PurchaseOrderAllocationVO> allocations;
}
