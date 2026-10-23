package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.OrderOperationLogEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface OrderOperationLogDao {
    int insert(OrderOperationLogEntity row);

    /**
     * 列表读；日志行本身无归属列，范围经父订单收窄，见 OrderOperationLogMapper.xml。
     */
    List<OrderOperationLogEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 订单日志只能在调用方完成订单归属校验后读取。商城客户按客户归属校验，不使用员工的数据范围过滤。
     */
    List<OrderOperationLogEntity> listByOrderId(@Param("orderId") Long orderId);
}
