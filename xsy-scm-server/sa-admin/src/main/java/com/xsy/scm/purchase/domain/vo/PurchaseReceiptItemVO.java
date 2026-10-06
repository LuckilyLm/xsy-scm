package com.xsy.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购收货单行。
 *
 * <p>
 * 含 5 个对账数量（恒等式）：{@code remaining = max(planned − cumulative, 0)}、
 * {@code over = max(cumulative − planned, 0)}、{@code difference = cumulative − planned}（可为负）。
 *
 * <p>
 * {@code actualWeight} 三态：标品为 {@code null} 渲染为 {@code —}；非标品为实重。不存价格，金额由采购行派生。
 */
@Data
public class PurchaseReceiptItemVO {
    private Long id;
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
