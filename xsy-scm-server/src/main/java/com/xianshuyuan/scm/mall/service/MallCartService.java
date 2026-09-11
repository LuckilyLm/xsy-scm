package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.service.CustomerPriceResolver;
import com.xianshuyuan.scm.customer.service.CustomerService;
import com.xianshuyuan.scm.customer.service.ResolvedCustomerPrice;
import com.xianshuyuan.scm.mall.entity.MallCartItemEntity;
import com.xianshuyuan.scm.mall.mapper.MallCartItemMapper;
import com.xianshuyuan.scm.mall.mapper.MallCatalogMapper;
import com.xianshuyuan.scm.mall.row.MallProductRow;
import com.xianshuyuan.scm.mall.vo.MallCartItemResponse;
import com.xianshuyuan.scm.mall.vo.MallCartResponse;
import com.xianshuyuan.scm.order.converter.SalesOrderConverter;
import com.xianshuyuan.scm.order.service.OrderAmountCalculator;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商城购物车。加购时校验客户可见性与上下架；失效商品保留展示并给出原因，不静默删除。
 */
@Service
@RequiredArgsConstructor
public class MallCartService {

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999");

    private final MallCartItemMapper cart;
    private final MallCatalogMapper catalog;
    private final MallCatalogService catalogService;
    private final CustomerService customers;
    private final CustomerPriceResolver pricing;
    private final ProductSkuMapper skus;

    public MallCartResponse list(long customerId) {
        String policy = catalogService.visibilityPolicy(customerId);
        List<MallCartItemEntity> rows = cart.selectActiveByCustomerId(customerId);
        Map<Long, MallProductRow> available = new HashMap<>();
        List<MallCartItemResponse> items = new ArrayList<>();
        for (MallCartItemEntity row : rows) {
            MallProductRow product = catalog.selectProduct(customerId, row.getSkuId(), policy);
            if (product == null) {
                items.add(unavailable(row, "商品已下架或当前账号不可购买"));
                continue;
            }
            available.put(row.getSkuId(), product);
            items.add(null);
        }
        Map<Long, ResolvedCustomerPrice> prices = new HashMap<>();
        if (!available.isEmpty()) {
            prices.putAll(pricing.resolve(customerId, List.copyOf(available.keySet()), OffsetDateTime.now()).stream()
                    .collect(Collectors.toMap(ResolvedCustomerPrice::skuId, Function.identity(), (a, b) -> a)));
        }
        List<MallCartItemResponse> result = new ArrayList<>();
        BigDecimal totalQuantity = BigDecimal.ZERO.setScale(4);
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        int unavailableCount = 0;
        int index = 0;
        for (MallCartItemEntity row : rows) {
            MallCartItemResponse placeholder = items.get(index++);
            if (placeholder != null) {
                result.add(placeholder);
                unavailableCount++;
                continue;
            }
            MallProductRow product = available.get(row.getSkuId());
            ResolvedCustomerPrice price = prices.get(row.getSkuId());
            BigDecimal quantity = row.getQuantity();
            BigDecimal lineAmount = OrderAmountCalculator.lineAmount(quantity, price.unitPrice());
            result.add(new MallCartItemResponse(row.getSkuId(), product.getProductName(), product.getSpecName(),
                    product.getSpecValues(), product.getSaleUnit(), product.getProductType(),
                    SalesOrderConverter.decimal(quantity), SalesOrderConverter.decimal(price.unitPrice()),
                    price.source(), SalesOrderConverter.decimal(lineAmount), true, null));
            totalQuantity = totalQuantity.add(quantity);
            totalAmount = totalAmount.add(lineAmount);
        }
        return new MallCartResponse(result, SalesOrderConverter.decimal(totalQuantity),
                SalesOrderConverter.decimal(totalAmount), unavailableCount);
    }

    @Transactional
    public MallCartResponse add(long customerId, long skuId, String quantity) {
        requirePurchasable(customerId, skuId);
        BigDecimal value = requireQuantity(quantity);
        MallCartItemEntity existing = cart.selectActiveByCustomerAndSku(customerId, skuId);
        if (existing == null) {
            MallCartItemEntity item = new MallCartItemEntity();
            item.setCustomerId(customerId);
            item.setSkuId(skuId);
            item.setQuantity(value);
            item.setVersion(0);
            item.setDeleted(false);
            item.setCreatedBy("MALL");
            item.setUpdatedBy("MALL");
            cart.insert(item);
        } else {
            existing.setQuantity(value);
            existing.setUpdatedBy("MALL");
            cart.updateById(existing);
        }
        return list(customerId);
    }

    @Transactional
    public MallCartResponse update(long customerId, long skuId, String quantity) {
        BigDecimal value = requireQuantity(quantity);
        MallCartItemEntity existing = cart.selectActiveByCustomerAndSku(customerId, skuId);
        if (existing == null) {
            return add(customerId, skuId, quantity);
        }
        existing.setQuantity(value);
        existing.setUpdatedBy("MALL");
        cart.updateById(existing);
        return list(customerId);
    }

    @Transactional
    public MallCartResponse remove(long customerId, long skuId) {
        cart.softDeleteByCustomerAndSkus(customerId, List.of(skuId));
        return list(customerId);
    }

    @Transactional
    public void removeSubmitted(long customerId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        cart.softDeleteByCustomerAndSkus(customerId, skuIds);
    }

    private void requirePurchasable(long customerId, long skuId) {
        customers.requireEnabled(customerId);
        if (catalog.selectProduct(customerId, skuId, catalogService.visibilityPolicy(customerId)) == null) {
            throw new BusinessException(MallErrorCodes.SKU_NOT_VISIBLE);
        }
    }

    private static BigDecimal requireQuantity(String quantity) {
        BigDecimal value;
        try {
            value = new BigDecimal(quantity);
        } catch (RuntimeException exception) {
            throw new BusinessException(MallErrorCodes.INVALID_QUANTITY);
        }
        if (value.signum() <= 0) {
            throw new BusinessException(MallErrorCodes.INVALID_QUANTITY);
        }
        if (value.compareTo(MAX_QUANTITY) > 0) {
            throw new BusinessException(MallErrorCodes.QUANTITY_OUT_OF_RANGE);
        }
        return value.setScale(4);
    }

    private MallCartItemResponse unavailable(MallCartItemEntity row, String reason) {
        ProductSkuEntity sku = skus.selectById(row.getSkuId());
        String name = sku == null ? "已失效商品" : sku.getSkuCode();
        String specName = sku == null ? null : sku.getSpecName();
        String saleUnit = sku == null ? null : sku.getSaleUnit();
        return new MallCartItemResponse(row.getSkuId(), name, specName, null, saleUnit,
                sku == null ? null : sku.getProductType(), SalesOrderConverter.decimal(row.getQuantity()),
                null, null, null, false, reason);
    }
}
