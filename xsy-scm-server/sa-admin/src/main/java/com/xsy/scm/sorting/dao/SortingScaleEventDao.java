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
 * 只追加不删除，设备读数要能追溯。状态流转（待处理 → 已接受 / 已驳回）用 SQL 直接写， 因为每次还要同时记下处理人。
 */
@Mapper
public interface SortingScaleEventDao extends BaseMapper<SortingScaleEventEntity> {

    /** 追加一条读数；{@code event_key} 冲突返回 0（重复上报），调用方重读既有记录。 */
    int insertIgnore(@Param("row") SortingScaleEventEntity row);

    SortingScaleEventEntity selectByEventKey(@Param("eventKey") String eventKey);

    /** 取一行并加锁（接受 / 驳回用）。 */
    SortingScaleEventEntity lockById(@Param("id") Long id);

    /** 某任务的读数；{@code status} 为空返回全部。 */
    List<SortingScaleEventEntity> listByTask(@Param("taskId") Long taskId, @Param("status") String status,
        @Param("limit") int limit);

    int markAccepted(@Param("id") Long id, @Param("version") Integer version,
        @Param("acceptedQuantity") java.math.BigDecimal acceptedQuantity, @Param("operator") String operator);

    int markRejected(@Param("id") Long id, @Param("version") Integer version, @Param("reason") String reason,
        @Param("operator") String operator);

    /** 按操作幂等号取筐记录；重试命中时返回原记录。 */
    SortingScaleEventEntity selectByOperationId(@Param("operationId") String operationId);

    /** 追加一筐；{@code event_key} 或 {@code operation_id} 冲突返回 0，调用方重查后判定重放还是换内容。 */
    int insertBasket(@Param("row") SortingScaleEventEntity row);

    /** 某明细尚未被集合完成覆盖的筐，按筐号升序；服务端据此复算总量与集合摘要。 */
    List<SortingScaleEventEntity> listPendingBaskets(@Param("taskItemId") Long taskItemId);

    /** 取该明细的待完成筐并加锁：两台秤同时完成同一明细时只能有一笔继续往下走。 */
    List<SortingScaleEventEntity> lockPendingBaskets(@Param("taskItemId") Long taskItemId);

    /** 把参与本次完成的筐标记为已接受，并写入完成记录号。 */
    int markBasketCompleted(@Param("ids") List<Long> ids, @Param("completionId") Long completionId,
        @Param("operator") String operator);
}
