package com.xianshuyuan.scm.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.dto.ProductPageQuery;
import com.xianshuyuan.scm.product.entity.ProductCategoryEntity;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.entity.ProductSpuEntity;
import com.xianshuyuan.scm.product.mapper.ProductCategoryMapper;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import com.xianshuyuan.scm.product.mapper.ProductSpuMapper;
import com.xianshuyuan.scm.product.vo.ProductDetailResponse;
import com.xianshuyuan.scm.product.vo.ProductSkuResponse;
import com.xianshuyuan.scm.product.vo.ProductSummaryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductQueryService {

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;

    public ProductQueryService(
        ProductSpuMapper spuMapper,
        ProductSkuMapper skuMapper,
        ProductCategoryMapper categoryMapper
    ) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
    }

    public PageData<ProductSummaryResponse> page(ProductPageQuery query) {
        String keyword = query.keyword() == null ? null : query.keyword().trim();
        ProductPageQuery normalized = new ProductPageQuery(
            query.page(), query.pageSize(), keyword, query.categoryId(),
            query.spuStatus(), query.skuStatus(), query.productType()
        );
        IPage<ProductSpuEntity> result = spuMapper.selectProductPage(
            new Page<>(query.page(), query.pageSize()), normalized
        );
        if (result.getRecords().isEmpty()) {
            return new PageData<>(List.of(), query.page(), query.pageSize(), result.getTotal());
        }
        List<Long> spuIds = result.getRecords().stream().map(ProductSpuEntity::getId).toList();
        Map<Long, List<ProductSkuEntity>> skusBySpu = skuMapper.selectActiveBySpuIds(spuIds)
            .stream().collect(Collectors.groupingBy(ProductSkuEntity::getSpuId));
        Map<Long, ProductCategoryEntity> categories = categoryMap();
        List<ProductSummaryResponse> records = result.getRecords().stream()
            .map(spu -> toSummary(spu, skusBySpu.getOrDefault(spu.getId(), List.of()), categories))
            .toList();
        return new PageData<>(records, query.page(), query.pageSize(), result.getTotal());
    }

    public ProductDetailResponse get(long spuId) {
        ProductSpuEntity spu = spuMapper.selectById(spuId);
        if (spu == null || Boolean.TRUE.equals(spu.getDeleted())) {
            throw new BusinessException(ProductErrorCodes.PRODUCT_NOT_FOUND);
        }
        List<ProductSkuResponse> skus = skuMapper.selectActiveBySpuId(spuId).stream()
            .map(this::toSkuResponse).toList();
        return new ProductDetailResponse(
            spu.getId(), spu.getVersion(), spu.getSpuCode(), spu.getName(), spu.getAlias(),
            spu.getCategoryId(), categoryPath(spu.getCategoryId(), categoryMap()),
            spu.getDescription(), spu.getStatus(), spu.getCreatedAt(), spu.getUpdatedAt(), skus
        );
    }

    private ProductSummaryResponse toSummary(
        ProductSpuEntity spu,
        List<ProductSkuEntity> skuEntities,
        Map<Long, ProductCategoryEntity> categories
    ) {
        List<ProductSkuResponse> skus = skuEntities.stream().map(this::toSkuResponse).toList();
        ProductSkuResponse defaultSku = skus.stream().filter(ProductSkuResponse::defaultSku)
            .findFirst().orElse(null);
        BigDecimal min = null;
        BigDecimal max = null;
        for (ProductSkuEntity sku : skuEntities) {
            BigDecimal price = sku.getMarketPrice();
            min = min == null || price.compareTo(min) < 0 ? price : min;
            max = max == null || price.compareTo(max) > 0 ? price : max;
        }
        return new ProductSummaryResponse(
            spu.getId(), spu.getVersion(), spu.getSpuCode(), spu.getName(), spu.getAlias(),
            spu.getCategoryId(), categoryPath(spu.getCategoryId(), categories), defaultSku,
            skus.size(), price(min), price(max), spu.getStatus(), spu.getUpdatedAt(), skus
        );
    }

    private ProductSkuResponse toSkuResponse(ProductSkuEntity sku) {
        return new ProductSkuResponse(
            sku.getId(), sku.getVersion(), sku.getSkuCode(), sku.getBarcode(),
            sku.getSpecName(), sku.getSpecValues(), sku.getSaleUnit(), sku.getProductType(),
            price(sku.getMarketPrice()), sku.getStatus(), Boolean.TRUE.equals(sku.getDefaultSku()),
            sku.getSortOrder()
        );
    }

    private Map<Long, ProductCategoryEntity> categoryMap() {
        return categoryMapper.selectActiveCategories().stream()
            .collect(Collectors.toMap(ProductCategoryEntity::getId, Function.identity()));
    }

    private String categoryPath(long categoryId, Map<Long, ProductCategoryEntity> categories) {
        List<String> names = new ArrayList<>();
        ProductCategoryEntity current = categories.get(categoryId);
        int remaining = 3;
        while (current != null && remaining-- > 0) {
            names.addFirst(current.getName());
            current = current.getParentId() == null ? null : categories.get(current.getParentId());
        }
        return String.join("/", names);
    }

    private String price(BigDecimal value) {
        return value == null ? null : value.setScale(4).toPlainString();
    }
}
