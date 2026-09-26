package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface OrderRefundDao extends BaseMapper<OrderRefundEntity> {
    OrderRefundEntity lock(@Param("id") Long id);

    /** 列表读；范围按父订单的 {@code sales_order.seller_id} 收窄，见 OrderRefundMapper.xml。 */
    List<OrderRefundEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
                                  @Param("scope") ScmValueScope scope);
}
