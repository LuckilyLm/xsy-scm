package com.xsy.scm.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.device.domain.entity.ScaleDeviceSessionEntity;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 员工授权会话读写。只按令牌摘要查询，不接受客户端自报员工身份。
 */
@Mapper
public interface ScaleDeviceSessionDao extends BaseMapper<ScaleDeviceSessionEntity> {

    ScaleDeviceSessionEntity selectByTokenHash(@Param("sessionTokenHash") String sessionTokenHash);

    /** 结束某设备上仍在有效期内的全部会话：重新登录、设备暂停或撤销时都走这里，避免旧会话在设备被接管后继续可用。 */
    int endActiveSessions(@Param("deviceId") Long deviceId, @Param("reason") String reason,
        @Param("endedAt") OffsetDateTime endedAt);
}
