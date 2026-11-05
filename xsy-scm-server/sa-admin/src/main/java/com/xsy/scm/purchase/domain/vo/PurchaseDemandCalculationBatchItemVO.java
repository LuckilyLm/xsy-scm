package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 计算结果批次的一行建议（只读回看）。
 *
 * <p>
 * 它是「某个 SKU 的净缺口被摊到哪张销售订单行上、摊了多少」的原始记录： {@code sourceQuantity} 是来源订单行的实发量，{@code requiredQuantity} 是本批 建议量（可能小于实发量，因为同
 * SKU 的缺口在批次内按确认时间顺序被前面的行消耗完了）。 {@code existingDemandId} 非空表示这一行在保存时已有活动需求，因此本批不再重复建议。
 */
@Data
public class PurchaseDemandCalculationBatchItemVO {

    /**
     * 批次内行号，从 1 起，与保存顺序一致。
     */
    private Integer lineNo;

    private String salesOrderNo;

    private OffsetDateTime sourceConfirmedAt;

    private Long skuId;

    private String skuCode;

    private String productName;

    private String skuName;

    private Map<String, Object> specValues;

    /**
     * 需求单位（下单时的销售单位）；与采购单位不一致时不得换算。
     */
    private String demandUnit;

    private String productType;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal sourceQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal requiredQuantity;

    /**
     * 保存时该来源行已存在的活动需求 id；非空即本批不再为它建需求。
     */
    private Long existingDemandId;
}
