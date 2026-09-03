package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.*;
import com.xianshuyuan.scm.customer.mapper.CustomerSkuVisibilityMapper;
import com.xianshuyuan.scm.customer.vo.OrderableSkuResponse;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OrderableSkuQueryService {
    private final CustomerService customers;
    private final ProductSkuMapper skus;
    private final CustomerSkuVisibilityMapper visibility;

    public OrderableSkuQueryService(CustomerService customers, ProductSkuMapper skus,
                                    CustomerSkuVisibilityMapper visibility) {
        this.customers = customers;
        this.skus = skus;
        this.visibility = visibility;
    }

    public List<ProductSkuEntity> requireOrderable(long customerId, List<Long> skuIds) {
        CustomerEntity customer = customers.requireEnabled(customerId);
        if (skuIds.isEmpty()) return List.of();
        List<ProductSkuEntity> rows = skus.selectOrderableByIds(skuIds);
        Set<Long> valid = new HashSet<>();
        rows.forEach(s -> valid.add(s.getId()));
        if (customer.getVisibilityPolicy() == VisibilityPolicy.ALLOWLIST) {
            valid.retainAll(new HashSet<>(visibility.selectVisibleSkuIds(customerId, skuIds)));
        }
        if (valid.size() != new HashSet<>(skuIds).size()) {
            throw new BusinessException(CustomerErrorCodes.SKU_NOT_VISIBLE);
        }
        return rows.stream().filter(s -> valid.contains(s.getId())).toList();
    }

    public List<OrderableSkuResponse> listOrderable(long customerId) {
        CustomerEntity customer = customers.requireEnabled(customerId);
        List<ProductSkuEntity> rows;
        if (customer.getVisibilityPolicy() == VisibilityPolicy.ALL_ENABLED) {
            rows = skus.selectAllOrderable();
        } else {
            List<CustomerSkuVisibilityEntity> visible = visibility.selectActiveByCustomerId(customerId);
            if (visible.isEmpty()) return List.of();
            rows = skus.selectOrderableByIds(visible.stream().map(CustomerSkuVisibilityEntity::getSkuId).toList());
        }
        return rows.stream().map(this::response).toList();
    }

    private OrderableSkuResponse response(ProductSkuEntity sku) {
        return new OrderableSkuResponse(sku.getId(), sku.getSpuId(), sku.getSkuCode(),
            sku.getSpecName(), sku.getSaleUnit(), sku.getMarketPrice().setScale(4).toPlainString());
    }
}
