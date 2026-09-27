package com.xsy.scm.common.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScmIdempotencyRecordDao extends BaseMapper<ScmIdempotencyRecordEntity> {

    ScmIdempotencyRecordEntity lock(@Param("id") Long id);

    int claim(ScmIdempotencyRecordEntity row);

    ScmIdempotencyRecordEntity find(@Param("scope") String scope, @Param("key") String key);
}
