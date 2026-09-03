package com.xianshuyuan.scm.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;import com.xianshuyuan.scm.order.entity.OrderRefundEntity;import org.apache.ibatis.annotations.*;
@Mapper public interface OrderRefundMapper extends BaseMapper<OrderRefundEntity>{long nextNumber();OrderRefundEntity selectActiveById(@Param("id")long id);OrderRefundEntity selectActiveByIdForUpdate(@Param("id")long id);OrderRefundEntity selectByReturnId(@Param("returnId")long returnId);}
