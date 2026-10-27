package com.xsy.scm.pricing.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
public class CustomerGroupPriceVO {
    private Long groupPriceId;
    private Integer version;
    private Long groupId;
    private String groupCode;
    private String groupName;
    private Long spuId;
    /** 仅详情返回：用该商品同单位的任一规格回填编辑表单。 */
    private Long skuId;
    private String productName;
    private String saleUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
    private OffsetDateTime updatedAt;
}
