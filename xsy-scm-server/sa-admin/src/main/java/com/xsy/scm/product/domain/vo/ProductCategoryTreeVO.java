package com.xsy.scm.product.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductCategoryTreeVO extends ProductCategoryVO {
    private List<ProductCategoryTreeVO> children;
}
