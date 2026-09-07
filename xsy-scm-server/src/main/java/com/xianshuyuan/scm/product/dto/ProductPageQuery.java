package com.xianshuyuan.scm.product.dto;

import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.entity.ShelfStatus;

public record ProductPageQuery(
        long page,
        long pageSize,
        String keyword,
        Long categoryId,
        ShelfStatus spuStatus,
        ShelfStatus skuStatus,
        ProductType productType
) {
}
