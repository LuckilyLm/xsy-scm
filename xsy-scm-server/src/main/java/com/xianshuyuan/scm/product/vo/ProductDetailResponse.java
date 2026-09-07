package com.xianshuyuan.scm.product.vo;

import com.xianshuyuan.scm.product.entity.ShelfStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ProductDetailResponse(
        long id,
        int version,
        String spuCode,
        String name,
        String alias,
        long categoryId,
        String categoryPath,
        String description,
        ShelfStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ProductSkuResponse> skus
) {
    public ProductDetailResponse {
        skus = List.copyOf(skus);
    }
}
