package com.xianshuyuan.scm.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.order.entity.IdempotencyRecordEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {
    int insertClaim(@Param("operationScope") String operationScope, @Param("idempotencyKey") String idempotencyKey, @Param("requestHash") String requestHash);

    IdempotencyRecordEntity selectActiveByScopeAndKey(@Param("operationScope") String operationScope, @Param("idempotencyKey") String idempotencyKey);

    IdempotencyRecordEntity selectActiveByScopeAndKeyForUpdate(@Param("operationScope") String operationScope, @Param("idempotencyKey") String idempotencyKey);
}
