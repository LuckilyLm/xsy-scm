package com.xianshuyuan.scm.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.order.entity.OrderReturnItemEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderReturnItemMapper extends BaseMapper<OrderReturnItemEntity> {
    List<OrderReturnItemEntity> selectByReturnId(@Param("returnId") long returnId);

    List<OrderReturnItemEntity> selectReservedByOrderItemIdsForUpdate(@Param("orderItemIds") List<Long> ids);
}
