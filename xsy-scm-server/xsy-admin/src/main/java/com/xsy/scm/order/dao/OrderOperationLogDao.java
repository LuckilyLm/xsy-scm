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
     * 列表查询；日志行本身无归属列，范围经父订单收窄，见 OrderOperationLogMapper.xml。
     */
    List<OrderOperationLogEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 调用方必须先校验订单归属；商城客户按客户校验，不使用员工数据范围。
     */
    List<OrderOperationLogEntity> listByOrderId(@Param("orderId") Long orderId);
}
