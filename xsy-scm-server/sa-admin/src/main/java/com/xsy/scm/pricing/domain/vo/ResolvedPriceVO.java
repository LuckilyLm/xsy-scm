package com.xsy.scm.pricing.domain.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;

/**
 * 价格可用性与可售性互相独立；0 也是有效价格。
 */
@Data
public class ResolvedPriceVO {
    private Long skuId;
    private String skuCode;
    private String productName;
    private String specName;
    /** 销售单位；录单页据此判断参考价与当前行是否同一单位。 */
    private String saleUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private ScmPriceStatusEnum priceStatus;
    private ScmPriceSourceEnum priceSource;
    private Long sourceRecordId;
    private ScmUnpricedReasonEnum unpricedReason;
    private boolean sellable;
    private ScmUnavailableReasonEnum unavailableReason;
    @JsonIgnore
    private Map<String, Object> priceBasisSnapshot;

    public void price(BigDecimal unitPrice, ScmPriceSourceEnum priceSource, Long sourceRecordId) {
        this.unitPrice = unitPrice;
        priceStatus = unitPrice == null ? ScmPriceStatusEnum.UNPRICED : ScmPriceStatusEnum.PRICED;
        this.priceSource = unitPrice == null ? null : priceSource;
        this.sourceRecordId = unitPrice == null ? null : sourceRecordId;
        unpricedReason = unitPrice == null ? ScmUnpricedReasonEnum.NO_PRICE_SOURCE : null;
    }
}
