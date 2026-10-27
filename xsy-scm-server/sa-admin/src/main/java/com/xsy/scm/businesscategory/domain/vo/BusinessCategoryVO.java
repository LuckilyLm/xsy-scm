package com.xsy.scm.businesscategory.domain.vo;

import lombok.Data;

@Data
public class BusinessCategoryVO {

    private Long categoryId;

    private Integer version;

    private String scope;

    private Long parentId;

    private String categoryCode;

    private String name;

    private Integer level;

    private Integer sortOrder;

    private String status;

    private String categoryPath;
}
