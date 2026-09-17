package com.xsy.scm.admin.module.business.purchase.dao;

import com.xsy.scm.admin.module.business.purchase.domain.bo.OrderRequireAggBO;
import com.xsy.scm.admin.module.business.purchase.domain.bo.ProductStockAggBO;
import com.xsy.scm.admin.module.business.purchase.domain.bo.ProductSupplierAggBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购单生成 聚合查询 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface PurchaseGenerateDao {

    /**
     * 按时间段汇总订单明细需求量（商品 + 规格维度）
     */
    List<OrderRequireAggBO> aggregateRequireByProduct(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按商品汇总现有库存余额
     */
    List<ProductStockAggBO> sumStockByProductIds(@Param("productIdList") List<Long> productIdList);

    /**
     * 按商品查询供应商（含供应价）
     */
    List<ProductSupplierAggBO> listSupplierByProductIds(@Param("productIdList") List<Long> productIdList);
}
