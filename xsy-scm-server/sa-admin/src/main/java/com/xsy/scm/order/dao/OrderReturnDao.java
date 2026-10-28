package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.form.OrderReturnQueryForm;
import com.xsy.scm.order.domain.vo.OrderReturnVO;

@Mapper
public interface OrderReturnDao extends BaseMapper<OrderReturnEntity> {
    OrderReturnEntity lock(@Param("id") Long id);

    /**
     * 列表查询；原单退货按父订单业务员收窄，独立退货按客户负责业务员收窄，见 OrderReturnMapper.xml。
     */
    List<OrderReturnVO> query(Page<?> page, @Param("query") OrderReturnQueryForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 商城客户读自己的退货单：按 {@code customer_id} 过滤，不拼员工范围。
     */
    List<OrderReturnEntity> listByCustomer(Page<?> page, @Param("customerId") Long customerId,
        @Param("orderId") Long orderId);
}
