package com.xianshuyuan.scm.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.order.entity.OrderOperationLogEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderOperationLogMapper extends BaseMapper<OrderOperationLogEntity> {
    List<OrderOperationLogEntity> selectByOrderId(@Param("orderId") long orderId);
}
