package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.domain.vo.OrderReturnVO;

@Mapper
public interface OrderReturnDao extends BaseMapper<OrderReturnEntity> {
    OrderReturnEntity lock(@Param("id") Long id);

    /**
     * 列表读；范围按父订单的 {@code xsy_sales_order.seller_id} 收窄，见 OrderReturnMapper.xml。
     */
    List<OrderReturnVO> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 商城客户读自己的退货单：客户身份即范围（{@code customer_id} 等值），不拼员工范围。
     */
    List<OrderReturnEntity> listByCustomer(Page<?> page, @Param("customerId") Long customerId,
        @Param("orderId") Long orderId);
}
