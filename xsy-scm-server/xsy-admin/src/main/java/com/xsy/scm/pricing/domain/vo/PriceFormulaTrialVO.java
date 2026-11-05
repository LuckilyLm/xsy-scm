package com.xsy.scm.pricing.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.Map;

public record PriceFormulaTrialVO(Long skuId,
    @JsonSerialize(using = ScmFixedScale4Serializer.class) BigDecimal unitPrice, String priceSource,
    String unpricedReason, Map<String, Object> calculationBasis) {
}
