package net.lab1024.sa.admin.module.scm.pricing.dao;

import org.apache.ibatis.annotations.*;

@Mapper
public interface PriceBatchAuditDao {
    long successCount(@Param("key") String key);

    void insert(@Param("key") String key, @Param("result") String result, @Param("count") int count, @Param("errors") String errors, @Param("operator") String operator);
}
