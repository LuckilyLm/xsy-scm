package com.xianshuyuan.scm.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;import com.xianshuyuan.scm.order.entity.OrderReturnEntity;import org.apache.ibatis.annotations.*;import java.util.List;
@Mapper public interface OrderReturnMapper extends BaseMapper<OrderReturnEntity>{long nextNumber();OrderReturnEntity selectActiveById(@Param("id")long id);OrderReturnEntity selectActiveByIdForUpdate(@Param("id")long id);List<OrderReturnEntity> selectByOrderId(@Param("orderId")long orderId);}
