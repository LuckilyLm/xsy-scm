package com.xianshuyuan.scm.product.vo;

import java.util.List;

public record ProductCategoryTreeNode(
        long id,
        Long parentId,
        String categoryCode,
        String name,
        int level,
        int sortOrder,
        String status,
        List<ProductCategoryTreeNode> children
) {
    public ProductCategoryTreeNode {
        children = List.copyOf(children);
    }
}
