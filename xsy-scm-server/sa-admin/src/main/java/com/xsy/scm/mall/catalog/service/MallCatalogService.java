package com.xsy.scm.mall.catalog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerFrequentSkuDao;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.catalog.dao.MallCatalogDao;
import com.xsy.scm.mall.catalog.domain.form.MallProductQueryForm;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.service.PriceResolver;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallCatalogService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final MallCatalogDao catalogDao;
    private final PriceResolver priceResolver;
    private final CustomerFrequentSkuDao frequentSkuDao;
    private final FileService fileService;
    private final MallWarehouseService mallWarehouseService;
    private final MallStockAvailabilityService stockAvailabilityService;

    @Transactional(readOnly = true)
    public List<MallCategoryVO> categories(RequestMallCustomer customer) {
        List<MallCategoryVO> rows = catalogDao.categories(customer.getCustomerId());
        Map<Long, MallCategoryVO> byId = rows.stream().collect(
                Collectors.toMap(MallCategoryVO::getCategoryId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        rows.forEach(row -> {
            MallCategoryVO parent = byId.get(row.getParentId());
            if (parent != null) {
                parent.getChildren().add(row);
            }
        });
        return rows.stream().filter(row -> row.getParentId() == null || !byId.containsKey(row.getParentId())).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<MallProductVO> products(RequestMallCustomer customer, MallProductQueryForm query) {
        Page<MallProductVO> page = new Page<>(query.getPageNum(), query.getPageSize(), true);
        Long warehouseId = query.getWarehouseId() == null
                ? mallWarehouseService.preferred(customer.getCustomerId(), null)
                : mallWarehouseService.require(customer.getCustomerId(), null, query.getWarehouseId());
        List<MallProductVO> rows = catalogDao.products(page, customer.getCustomerId(), query);
        enrich(customer, rows, warehouseId);
        PageResult<MallProductVO> result = new PageResult<>();
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(rows);
        result.setEmptyFlag(rows.isEmpty());
        return result;
    }

    @Transactional(readOnly = true)
    public MallProductVO product(RequestMallCustomer customer, Long skuId) {
        MallProductVO row = catalogDao.product(customer.getCustomerId(), skuId);
        if (row == null) {
            throw new ScmBusinessException(MallErrorCode.CATALOG_PRODUCT_NOT_FOUND);
        }
        enrich(customer, List.of(row), mallWarehouseService.preferred(customer.getCustomerId(), null));
        return row;
    }

    @Transactional(readOnly = true)
    public List<MallProductVO> visibleProducts(RequestMallCustomer customer, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        List<MallProductVO> rows = catalogDao.productsBySkuIds(customer.getCustomerId(),
                skuIds.stream().distinct().toList());
        enrich(customer, rows, mallWarehouseService.preferred(customer.getCustomerId(), null));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<MallProductVO> visibleProducts(RequestMallCustomer customer, List<Long> skuIds, Long addressId,
            Long warehouseId) {
        if (skuIds == null || skuIds.isEmpty())
            return List.of();
        Long selectedWarehouse = warehouseId == null
                ? mallWarehouseService.preferred(customer.getCustomerId(), addressId)
                : mallWarehouseService.require(customer.getCustomerId(), addressId, warehouseId);
        List<MallProductVO> rows = catalogDao.productsBySkuIds(customer.getCustomerId(),
                skuIds.stream().distinct().toList());
        enrich(customer, rows, selectedWarehouse);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<MallProductVO> frequent(RequestMallCustomer customer, int days, int limit) {
        int window = Math.min(Math.max(days, 1), 365);
        int size = Math.min(Math.max(limit, 1), 100);
        var since = LocalDate.now(SHANGHAI).minusDays(window - 1L).atStartOfDay(SHANGHAI).toOffsetDateTime();
        List<Long> skuIds = frequentSkuDao.frequentSkus(customer.getCustomerId(), since, size).stream()
                .map(item -> item.getSkuId()).distinct().toList();
        if (skuIds.isEmpty()) {
            return List.of();
        }
        List<MallProductVO> rows = catalogDao.productsBySkuIds(customer.getCustomerId(), skuIds);
        enrich(customer, rows, mallWarehouseService.preferred(customer.getCustomerId(), null));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<MallProductVO> newest(RequestMallCustomer customer, int limit) {
        List<Long> skuIds = catalogDao.newestSkuIds(customer.getCustomerId(), Math.min(Math.max(limit, 1), 20));
        if (skuIds.isEmpty()) {
            return List.of();
        }
        List<MallProductVO> rows = catalogDao.productsBySkuIds(customer.getCustomerId(), skuIds);
        enrich(customer, rows, mallWarehouseService.preferred(customer.getCustomerId(), null));
        return rows;
    }

    private void enrich(RequestMallCustomer customer, List<MallProductVO> rows, Long warehouseId) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long,
                ResolvedPriceVO> prices = priceResolver
                        .resolve(customer.getCustomerId(), rows.stream().map(MallProductVO::getSkuId).toList(), null)
                        .stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
        Map<String, String> urls = new HashMap<>();
        fileService
                .getFileList(rows.stream().map(MallProductVO::getPrimaryImageKey).filter(key -> key != null).toList())
                .forEach(file -> urls.put(file.getFileKey(), file.getFileUrl()));
        for (MallProductVO row : rows) {
            ResolvedPriceVO price = prices.get(row.getSkuId());
            if (price != null) {
                row.setUnitPrice(price.getUnitPrice());
                row.setPriceStatus(price.getPriceStatus().name());
                row.setPriceSource(price.getPriceSource() == null ? null : price.getPriceSource().name());
                row.setSellable(price.isSellable());
                row.setUnavailableReason(
                        price.getUnavailableReason() == null ? null : price.getUnavailableReason().name());
            }
            row.setPrimaryImageUrl(urls.get(row.getPrimaryImageKey()));
        }
        stockAvailabilityService.enrich(warehouseId, rows);
    }
}
