package com.xsy.scm.mall.catalog.domain.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class MallCategoryVO {
    private Long categoryId;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sortOrder;
    private List<MallCategoryVO> children = new ArrayList<>();
}
