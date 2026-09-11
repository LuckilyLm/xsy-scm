package com.xianshuyuan.scm.mall.row;

import lombok.Data;

@Data
public class MallCategoryRow {

    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sortOrder;
    private long productCount;
}
