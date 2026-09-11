package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.customer.entity.CustomerAgreementPriceEntity;
import com.xianshuyuan.scm.customer.entity.CustomerEntity;
import com.xianshuyuan.scm.customer.entity.CustomerTypePriceEntity;
import com.xianshuyuan.scm.customer.mapper.CustomerAgreementPriceMapper;
import com.xianshuyuan.scm.customer.mapper.CustomerTypePriceMapper;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerPriceResolver {
    private final CustomerService customers;
    private final CustomerAgreementPriceMapper prices;
    private final CustomerTypePriceMapper typePrices;
    private final ProductSkuMapper skus;
    private final OrderableSkuQueryService orderable;

    public CustomerPriceResolver(CustomerService c, CustomerAgreementPriceMapper p, CustomerTypePriceMapper tp,
                                 ProductSkuMapper s, OrderableSkuQueryService o) {
        customers = c;
        prices = p;
        typePrices = tp;
        skus = s;
        orderable = o;
    }

    public List<ResolvedCustomerPrice> resolve(long customerId, List<Long> skuIds, OffsetDateTime at) {
        if (skuIds.isEmpty()) return List.of();
        CustomerEntity customer = customers.requireEnabled(customerId);
        var rows = orderable.requireOrderable(customerId, skuIds);
        Map<Long, CustomerAgreementPriceEntity> agreement = prices.selectEffective(customerId, skuIds, at).stream()
                .collect(Collectors.toMap(CustomerAgreementPriceEntity::getSkuId, Function.identity(), (a, b) -> a));
        Map<Long, CustomerTypePriceEntity> type = typePrices.selectEffective(customer.getCustomerTypeId(), skuIds, at).stream()
                .collect(Collectors.toMap(CustomerTypePriceEntity::getSkuId, Function.identity(), (a, b) -> a));
        Map<Long, com.xianshuyuan.scm.product.entity.ProductSkuEntity> byId = rows.stream()
                .collect(Collectors.toMap(com.xianshuyuan.scm.product.entity.ProductSkuEntity::getId, Function.identity()));
        return skuIds.stream().map(id -> {
            var a = agreement.get(id);
            if (a != null) return new ResolvedCustomerPrice(id, a.getUnitPrice(), PriceSource.AGREEMENT, a.getId());
            var t = type.get(id);
            if (t != null) return new ResolvedCustomerPrice(id, t.getUnitPrice(), PriceSource.CUSTOMER_TYPE, t.getId());
            var sku = byId.get(id);
            return new ResolvedCustomerPrice(id, sku == null ? null : sku.getMarketPrice(), PriceSource.MARKET, null);
        }).toList();
    }
}
