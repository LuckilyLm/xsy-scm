package com.xsy.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

/** 收货明细展示计划、本次实收、累计、剩余、超收及差异数量，各指标遵守同一对账恒等式，不混用实际重量和申报量。 */
@Data
public class PurchaseReceiptItemVO {
    private Long id;

    /** 本行登记的批次：未启用批次追踪的仓库为空列表。 */
    private java.util.List<com.xsy.scm.trace.domain.vo.TraceLotVO.ReceiptItemLot> lots;
    private Long purchaseOrderItemId;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private Map<String, Object> specValues;
    private String purchaseUnit;
    private String productType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal cumulativeReceivedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal remainingQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal overReceiptQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receiptDifference;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualWeight;
    private String weightUnit;
    private String weighingSource;
    private String correctionReason;
    private Integer version;
}
