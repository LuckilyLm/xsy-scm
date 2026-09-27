package com.xsy.scm.pricing.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;

/**
 * Price availability and sale eligibility are independent. Zero is a price.
 */
@Data
public class ResolvedPriceVO {
    private Long skuId;
    private String skuCode;
    private String productName;
    private String specName;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private ScmPriceStatusEnum priceStatus;
    private ScmPriceSourceEnum priceSource;
    private Long sourceRecordId;
    private ScmUnpricedReasonEnum unpricedReason;
    private boolean sellable;
    private ScmUnavailableReasonEnum unavailableReason;

    public void price(BigDecimal unitPrice, ScmPriceSourceEnum priceSource, Long sourceRecordId) {
        this.unitPrice = unitPrice;
        priceStatus = unitPrice == null ? ScmPriceStatusEnum.UNPRICED : ScmPriceStatusEnum.PRICED;
        this.priceSource = unitPrice == null ? null : priceSource;
        this.sourceRecordId = unitPrice == null ? null : sourceRecordId;
        unpricedReason = unitPrice == null ? ScmUnpricedReasonEnum.NO_PRICE_SOURCE : null;
    }
}
