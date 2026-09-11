package com.xianshuyuan.scm.mall.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xianshuyuan.scm.order.entity.OrderStatus;
import com.xianshuyuan.scm.order.entity.SalesOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MallOrderMapper {

    /**
     * 商城订单查询。客户身份是强制条件，越权访问在 SQL 层即被排除。
     */
    List<SalesOrderEntity> selectPageByCustomer(IPage<?> page, @Param("customerId") long customerId,
                                                @Param("status") OrderStatus status,
                                                @Param("keyword") String keyword);

    SalesOrderEntity selectActiveByIdAndCustomer(@Param("id") long id, @Param("customerId") long customerId);
}
