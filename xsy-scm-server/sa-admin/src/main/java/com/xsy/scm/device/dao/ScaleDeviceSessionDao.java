package com.xsy.scm.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.device.domain.entity.ScaleDeviceSessionEntity;
import com.xsy.scm.device.domain.vo.ScaleDeviceSessionVO;

import java.time.OffsetDateTime;
import java.util.List;

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

    /** 最近的员工会话，用于后台查看谁在这台秤上登录过。 */
    List<ScaleDeviceSessionVO> selectRecent(@Param("deviceId") Long deviceId, @Param("limit") int limit);

    /** 结束单条会话；已结束的会话不覆盖原结束原因。 */
    int endSession(@Param("deviceId") Long deviceId, @Param("sessionId") Long sessionId, @Param("reason") String reason,
        @Param("endedAt") OffsetDateTime endedAt);
}
