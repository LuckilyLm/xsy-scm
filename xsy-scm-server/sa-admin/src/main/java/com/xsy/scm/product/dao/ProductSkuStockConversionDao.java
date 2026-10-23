package com.xsy.scm.product.dao;

import com.xsy.scm.product.domain.vo.ProductSkuStockConversionVO;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductSkuStockConversionDao {
    List<ProductSkuStockConversionVO> list(@Param("skuId") Long skuId, @Param("saleUnit") String saleUnit);

    int upsert(@Param("skuId") Long skuId, @Param("saleUnit") String saleUnit, @Param("stockUnit") String stockUnit,
        @Param("factor") BigDecimal factor, @Param("operator") String operator);

    int delete(@Param("skuId") Long skuId, @Param("saleUnit") String saleUnit, @Param("stockUnit") String stockUnit,
        @Param("operator") String operator);
}
