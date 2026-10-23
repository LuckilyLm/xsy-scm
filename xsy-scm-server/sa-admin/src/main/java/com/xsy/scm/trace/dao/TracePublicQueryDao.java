package com.xsy.scm.trace.dao;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 公开扫码查询审计。
 *
 * <p>
 * 只追加、不参与业务判定：写入失败不能影响扫码结果，服务层会吞掉异常。 SQL 见 mapper/scm/trace/TracePublicQueryDao.xml。
 */
@Mapper
public interface TracePublicQueryDao {

    int insert(@Param("unitId") Long unitId, @Param("publicToken") String publicToken,
        @Param("clientIp") String clientIp, @Param("userAgent") String userAgent,
        @Param("queriedAt") OffsetDateTime queriedAt);
}
