package com.xsy.scm.product.domain.vo;

import lombok.Data;

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
