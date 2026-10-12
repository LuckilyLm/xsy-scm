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

    /** 列表读；日志行本身无归属列，范围经父订单收窄，见 OrderOperationLogMapper.xml。 */
    List<OrderOperationLogEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
            @Param("scope") ScmValueScope scope);

    /**
     * 按订单读全部处理记录，<b>不带</b>员工数据范围。
     *
     * <p>
     * 只给「已经按归属校验过订单」的调用方使用（商城入口先用 {@code selectByIdAndCustomer} 确认订单属于当前客户）。 不在这里再收窄一次，是因为商城客户不是员工、没有
     * {@code orderSellerScope}，套用员工范围会让客户看不到自己订单的记录。
     */
    List<OrderOperationLogEntity> listByOrderId(@Param("orderId") Long orderId);
}
