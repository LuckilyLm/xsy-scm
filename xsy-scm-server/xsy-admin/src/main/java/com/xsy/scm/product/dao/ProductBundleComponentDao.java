package com.xsy.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.product.domain.entity.ProductBundleComponentEntity;
import com.xsy.scm.product.domain.vo.ProductBundleComponentVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductBundleComponentDao extends BaseMapper<ProductBundleComponentEntity> {
    List<ProductBundleComponentEntity> listByBundleSku(@Param("bundleSkuId") Long bundleSkuId);

    List<ProductBundleComponentVO> detailByBundleSku(@Param("bundleSkuId") Long bundleSkuId);

    List<ProductBundleComponentEntity> listByBundleSkus(@Param("bundleSkuIds") List<Long> bundleSkuIds);

    int softDeleteByBundleSku(@Param("bundleSkuId") Long bundleSkuId, @Param("operator") String operator);

    long countByComponentSku(@Param("componentSkuId") Long componentSkuId);

    long countReferencesBySkuIds(@Param("skuIds") List<Long> skuIds);
}
