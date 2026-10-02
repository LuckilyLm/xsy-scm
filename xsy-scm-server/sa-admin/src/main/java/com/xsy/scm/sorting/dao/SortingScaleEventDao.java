package com.xsy.scm.sorting.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.sorting.domain.entity.SortingScaleEventEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 电子秤事件读写。
 *
 * <p>
 * 只追加、不删除：设备读数是要能追溯的事实。状态流转（待处理 → 已接受 / 已驳回）走手写 SQL，
 * 因为每次流转都要同时写「谁在什么时候处理的」，用通用 update 表达不了这层约束。
 */
@Mapper
public interface SortingScaleEventDao extends BaseMapper<SortingScaleEventEntity> {

    /**
     * 追加一条读数；{@code event_key} 冲突返回 0（重复上报），调用方重读既有记录。
     */
    int insertIgnore(@Param("row") SortingScaleEventEntity row);

    SortingScaleEventEntity selectByEventKey(@Param("eventKey") String eventKey);

    /**
     * 取一行并加锁（接受 / 驳回用）。
     */
    SortingScaleEventEntity lockById(@Param("id") Long id);

    /**
     * 某任务的读数；{@code status} 为空返回全部。
     */
    List<SortingScaleEventEntity> listByTask(@Param("taskId") Long taskId, @Param("status") String status,
            @Param("limit") int limit);

    int markAccepted(@Param("id") Long id, @Param("version") Integer version,
            @Param("acceptedQuantity") java.math.BigDecimal acceptedQuantity, @Param("operator") String operator);

    int markRejected(@Param("id") Long id, @Param("version") Integer version, @Param("reason") String reason,
            @Param("operator") String operator);
}
