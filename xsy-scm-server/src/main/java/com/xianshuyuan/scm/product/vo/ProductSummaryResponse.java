package com.xianshuyuan.scm.product.vo;

import com.xianshuyuan.scm.product.entity.ShelfStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ProductSummaryResponse(
        long id,
        int version,
        String spuCode,
        String name,
        String alias,
        long categoryId,
        String categoryPath,
        ProductSkuResponse defaultSku,
        int skuCount,
        String minMarketPrice,
        String maxMarketPrice,
        ShelfStatus status,
        OffsetDateTime updatedAt,
        List<ProductSkuResponse> skus
) {
    public ProductSummaryResponse {
        skus = List.copyOf(skus);
    }
}
