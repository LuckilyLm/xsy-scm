package com.xianshuyuan.scm.mall.vo;

public record MallCategoryResponse(Long id, Long parentId, String name, Integer level, Integer sortOrder,
                                   long productCount) {
}
