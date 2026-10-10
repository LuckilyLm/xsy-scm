package com.xsy.scm.customer.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CustomerSkuVisibilityReverseVO {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerTypeName;
    private String visibilityPolicy;
    private Long skuId;
    private String skuCode;
    private String productName;
    private String specName;
    private String skuStatus;
    private String spuStatus;
    private OffsetDateTime createdAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
}
