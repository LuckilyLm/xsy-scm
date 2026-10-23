package com.xsy.scm.product.service;

import com.xsy.scm.product.dao.ProductBundleComponentDao;
import com.xsy.scm.product.domain.entity.ProductBundleComponentEntity;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商品域对外提供只读组合组成清单，其他领域不得直接操作商品组成 DAO。
 */
@Service
@RequiredArgsConstructor
public class ProductBundleComponentQueryService {

    private final ProductBundleComponentDao productBundleComponentDao;

    public List<ProductBundleComponentEntity> listByBundleSku(Long bundleSkuId) {
        return productBundleComponentDao.listByBundleSku(bundleSkuId);
    }

    public List<ProductBundleComponentEntity> listByBundleSkus(List<Long> bundleSkuIds) {
        return bundleSkuIds == null || bundleSkuIds.isEmpty()
            ? List.of()
            : productBundleComponentDao.listByBundleSkus(bundleSkuIds);
    }
}
