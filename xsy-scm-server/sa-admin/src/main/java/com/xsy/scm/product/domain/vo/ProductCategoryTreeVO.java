package com.xsy.scm.product.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductCategoryTreeVO extends ProductCategoryVO {
    private List<ProductCategoryTreeVO> children;
}
