package com.xsy.scm.product.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
public class ProductCategoryVO {
    private Long categoryId;
    private Integer version;
    private Long parentId;
    private String categoryCode;
    private String name;
    private Integer level;
    private Integer sortOrder;
    private String status;
    private String categoryPath;
}
