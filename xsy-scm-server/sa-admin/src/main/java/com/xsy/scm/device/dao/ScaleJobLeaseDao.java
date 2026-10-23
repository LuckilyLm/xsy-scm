package com.xsy.scm.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.device.domain.entity.ScaleJobLeaseEntity;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 任务领取租约读写。
 *
 * <p>
 * 领取依赖 {@code uk_scale_job_lease_active_task} 这个部分唯一索引：并发领取同一任务时只有一笔能插入成功， 另一笔按「已被领取」返回，不能靠先查后插。
 */
@Mapper
public interface ScaleJobLeaseDao extends BaseMapper<ScaleJobLeaseEntity> {

    /** 当前有效租约（未释放且未过期）。 */
    ScaleJobLeaseEntity selectActiveByTask(@Param("taskId") Long taskId, @Param("now") OffsetDateTime now);

    /** 某员工在某设备上的有效租约。 */
    ScaleJobLeaseEntity selectActiveByTaskAndDevice(@Param("taskId") Long taskId, @Param("deviceId") Long deviceId,
        @Param("now") OffsetDateTime now);

    int release(@Param("id") Long id, @Param("reason") String reason, @Param("releasedAt") OffsetDateTime releasedAt);

    /**
     * 释放该任务上已过期但未标记释放的租约。
     *
     * <p>
     * 部分唯一索引只认 {@code released_at IS NULL}，过期未释放的行仍会占住任务，所以领取前必须先收掉这些行， 否则设备在租约过期后再也领不到同一任务。
     */
    int releaseExpired(@Param("taskId") Long taskId, @Param("now") OffsetDateTime now);
}
