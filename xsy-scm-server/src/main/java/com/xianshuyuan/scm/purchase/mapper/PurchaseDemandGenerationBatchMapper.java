package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandGenerationBatchEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseDemandGenerationBatchMapper extends BaseMapper<PurchaseDemandGenerationBatchEntity> {
    PurchaseDemandGenerationBatchEntity selectByIdempotency(@Param("scope") String scope, @Param("key") String key);
}
