package com.xsy.scm.pricing.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PriceBatchAuditDao {
    long successCount(@Param("key") String batchKey);

    void insert(@Param("key") String batchKey, @Param("result") String batchResult, @Param("count") int rowCount,
            @Param("errors") String errorData, @Param("operator") String operator);
}
