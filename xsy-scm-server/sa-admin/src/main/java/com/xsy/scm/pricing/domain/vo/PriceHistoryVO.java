package com.xsy.scm.pricing.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import com.xsy.scm.common.json.ScmOperatorSnapshotSerializer;

@Data
public class PriceHistoryVO {
    private Long historyId;
    private String source;
    private Long priceId;
    private Long customerId;
    private Long customerTypeId;
    private Long skuId;
    private String customerName;
    private String customerTypeName;
    private String skuCode;
    private String productName;
    private String specName;
    private String operationType;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;
    private OffsetDateTime operatedAt;
    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> beforeData;
    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> afterData;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal currentUnitPrice;
    private OffsetDateTime currentEffectiveFrom;
    private OffsetDateTime currentEffectiveTo;
    private Boolean currentDeleted;
}
