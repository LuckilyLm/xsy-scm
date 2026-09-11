package com.xianshuyuan.scm.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.CustomerEntity;
import com.xianshuyuan.scm.customer.entity.VisibilityPolicy;
import com.xianshuyuan.scm.customer.service.CustomerPriceResolver;
import com.xianshuyuan.scm.customer.service.CustomerService;
import com.xianshuyuan.scm.customer.service.ResolvedCustomerPrice;
import com.xianshuyuan.scm.mall.dto.MallProductPageQuery;
import com.xianshuyuan.scm.mall.mapper.MallCatalogMapper;
import com.xianshuyuan.scm.mall.row.MallCategoryRow;
import com.xianshuyuan.scm.mall.row.MallProductRow;
import com.xianshuyuan.scm.mall.vo.MallCategoryResponse;
import com.xianshuyuan.scm.mall.vo.MallHomeResponse;
import com.xianshuyuan.scm.mall.vo.MallHomeSectionResponse;
import com.xianshuyuan.scm.mall.vo.MallProductResponse;
import com.xianshuyuan.scm.mall.vo.MallThemeConfig;
import com.xianshuyuan.scm.marketing.service.MarketingService;
import com.xianshuyuan.scm.order.converter.SalesOrderConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商城商品目录。可见性在 SQL 层过滤，价格统一由客户价格解析服务给出。
 */
@Service
@RequiredArgsConstructor
public class MallCatalogService {

    private final MallCatalogMapper catalog;
    private final CustomerService customers;
    private final CustomerPriceResolver pricing;
    private final MarketingService marketing;

    public MallThemeConfig theme() {
        return marketing.currentTheme();
    }

    public MallHomeResponse home(long customerId) {
        return new MallHomeResponse(theme(), homeSections(), categories(customerId),
                marketing.effectivePromotions(), OffsetDateTime.now());
    }

    /**
     * 后台预览：不绑定具体客户，分类按全量可见性统计，其余与商城端完全一致。
     * 供后台会话访问，避免后台复用商城客户令牌链。
     */
    public MallHomeResponse homePreview() {
        List<MallCategoryResponse> preview = catalog.selectCategories(0L, VisibilityPolicy.ALL_ENABLED.name())
                .stream().map(MallCatalogService::toCategory).toList();
        return new MallHomeResponse(theme(), homeSections(), preview,
                marketing.effectivePromotions(), OffsetDateTime.now());
    }

    private List<MallHomeSectionResponse> homeSections() {
        return marketing.listHomeSections().stream()
                .map(section -> new MallHomeSectionResponse(section.id(), section.sectionType(), section.title(),
                        section.sortOrder(), section.payload(), section.promotionId(), section.categoryId()))
                .sorted(Comparator.comparing(MallHomeSectionResponse::sortOrder,
                                Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(MallHomeSectionResponse::id, Comparator.nullsFirst(Long::compareTo)))
                .toList();
    }

    public List<MallCategoryResponse> categories(long customerId) {
        String policy = visibilityPolicy(customerId);
        return catalog.selectCategories(customerId, policy).stream()
                .map(MallCatalogService::toCategory)
                .toList();
    }

    public PageData<MallProductResponse> products(MallProductPageQuery query) {
        MallProductPageQuery scoped = new MallProductPageQuery(query.page(), query.pageSize(), query.keyword(),
                query.categoryId(), query.customerId(), VisibilityPolicy.valueOf(visibilityPolicy(query.customerId())));
        Page<MallProductRow> page = new Page<>(scoped.page(), scoped.pageSize());
        List<MallProductRow> rows = catalog.selectProductPage(page, scoped);
        Map<Long, ResolvedCustomerPrice> prices = resolvePrices(query.customerId(), rows);
        List<MallProductResponse> items = rows.stream()
                .map(row -> toProduct(row, prices.get(row.getSkuId())))
                .toList();
        return new PageData<>(items, query.page(), query.pageSize(), page.getTotal());
    }

    public MallProductResponse product(long customerId, long skuId) {
        MallProductRow row = catalog.selectProduct(customerId, skuId, visibilityPolicy(customerId));
        if (row == null) {
            throw new BusinessException(MallErrorCodes.SKU_NOT_FOUND);
        }
        Map<Long, ResolvedCustomerPrice> prices = resolvePrices(customerId, List.of(row));
        return toProduct(row, prices.get(row.getSkuId()));
    }

    String visibilityPolicy(long customerId) {
        CustomerEntity customer = customers.requireEnabled(customerId);
        return customer.getVisibilityPolicy().name();
    }

    Map<Long, ResolvedCustomerPrice> resolvePrices(long customerId, List<MallProductRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> skuIds = rows.stream().map(MallProductRow::getSkuId).distinct().toList();
        return pricing.resolve(customerId, skuIds, OffsetDateTime.now()).stream()
                .collect(Collectors.toMap(ResolvedCustomerPrice::skuId, Function.identity(), (a, b) -> a));
    }

    private static MallCategoryResponse toCategory(MallCategoryRow row) {
        return new MallCategoryResponse(row.getId(), row.getParentId(), row.getName(), row.getLevel(),
                row.getSortOrder(), row.getProductCount());
    }

    private static MallProductResponse toProduct(MallProductRow row, ResolvedCustomerPrice price) {
        return new MallProductResponse(row.getSkuId(), row.getSpuId(), row.getProductName(), row.getSkuCode(),
                row.getSpecName(), row.getSpecValues(), row.getSaleUnit(), row.getProductType(), row.getCategoryId(),
                row.getCategoryName(), SalesOrderConverter.decimal(row.getMarketPrice()),
                price == null ? null : SalesOrderConverter.decimal(price.unitPrice()),
                price == null ? null : price.source());
    }
}
