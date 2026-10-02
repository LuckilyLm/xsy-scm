package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseDemandCalculationBatchItemDao extends BaseMapper<PurchaseDemandCalculationBatchItemEntity> {
    List<PurchaseDemandCalculationBatchItemEntity> listByBatchId(@Param("batchId") Long batchId);
    int insertBatch(@Param("rows") List<PurchaseDemandCalculationBatchItemEntity> rows);
}
