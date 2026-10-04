package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchEntity;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandCalculationBatchDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseDemandCalculationBatchDao extends BaseMapper<PurchaseDemandCalculationBatchEntity> {
    PurchaseDemandCalculationBatchEntity lock(@Param("batchId") Long batchId);

    /** 批次头 + 仓库 / 供应商 / 采购员当前名称；不存在返回 {@code null}。 */
    PurchaseDemandCalculationBatchDetailVO selectHeader(@Param("batchId") Long batchId);

    int markGenerated(@Param("batchId") Long batchId, @Param("generatedCount") Integer generatedCount,
            @Param("skippedCount") Integer skippedCount,
            @Param("resultSnapshot") java.util.Map<String, Object> resultSnapshot,
            @Param("generatedAt") java.time.OffsetDateTime generatedAt, @Param("operator") String operator);
}
