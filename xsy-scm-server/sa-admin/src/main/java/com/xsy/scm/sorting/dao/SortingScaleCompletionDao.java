package com.xsy.scm.sorting.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.sorting.domain.entity.SortingScaleCompletionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品集合完成记录读写。
 *
 * <p>
 * 按 {@code operation_id} 查询是幂等入口：同一操作号重复提交必须返回原结果，不能因为网络重试再确认一次分拣数量。
 */
@Mapper
public interface SortingScaleCompletionDao extends BaseMapper<SortingScaleCompletionEntity> {

    SortingScaleCompletionEntity selectByOperationId(@Param("operationId") String operationId);

    SortingScaleCompletionEntity selectByTaskItem(@Param("taskItemId") Long taskItemId);
}
