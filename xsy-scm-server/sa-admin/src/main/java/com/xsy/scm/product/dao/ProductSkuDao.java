package com.xsy.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductSkuDao extends BaseMapper<ProductSkuEntity> {
    int clearDefault(@Param("spuId") Long spuId);

    ProductSkuEntity lockById(@Param("skuId") Long skuId);

    List<ProductSkuEntity> lockByIds(@Param("skuIds") List<Long> skuIds);

    int bumpVersion(@Param("skuId") Long skuId, @Param("version") Integer version, @Param("operator") String operator);
}
