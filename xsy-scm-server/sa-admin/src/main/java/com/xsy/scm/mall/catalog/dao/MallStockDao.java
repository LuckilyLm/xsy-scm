package com.xsy.scm.mall.catalog.dao;

import com.xsy.scm.mall.catalog.domain.vo.MallStockFact;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只读消费库存余额；reserved_quantity 已包含正式预留，不再重复扣预留表。 */
@Mapper
public interface MallStockDao {
    List<MallStockFact> bySkus(@Param("warehouseId") Long warehouseId, @Param("skuIds") List<Long> skuIds);

    List<MallStockFact> configuredConversions(@Param("skuId") Long skuId, @Param("saleUnit") String saleUnit);
}
