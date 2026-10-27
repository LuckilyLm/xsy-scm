package com.xsy.scm.pricing.dao;

import com.xsy.scm.pricing.domain.vo.PricingCostBasisVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PricingCostBasisDao {
    List<PricingCostBasisVO> byWarehouseAndSkus(@Param("warehouseId") Long warehouseId,
        @Param("skuIds") List<Long> skuIds);

    /**
     * 每个商品规格最近一条已提交采购行的采购价；只读，供采购成本公式使用。
     */
    List<PricingCostBasisVO> latestPurchasePrices(@Param("skuIds") List<Long> skuIds,
        @Param("statuses") List<String> committedStatuses);
}
