package com.xsy.scm.pricing.dao;

import com.xsy.scm.pricing.domain.vo.PricingCostBasisVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PricingCostBasisDao {
    List<PricingCostBasisVO> byWarehouseAndSkus(@Param("warehouseId") Long warehouseId,
            @Param("skuIds") List<Long> skuIds);
}
