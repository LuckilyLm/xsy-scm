package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;
import com.xsy.scm.order.domain.form.OrderRefundQueryForm;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderRefundDao extends BaseMapper<OrderRefundEntity> {
    OrderRefundEntity lock(@Param("id") Long id);

    /** 列表读；范围按父订单的 {@code xsy_sales_order.seller_id} 收窄，见 OrderRefundMapper.xml。 */
    List<OrderRefundEntity> query(Page<?> page, @Param("query") OrderRefundQueryForm query,
            @Param("scope") ScmValueScope scope);

    /** 按退货单批量读退款单：归属由调用方按退货单客户判定，本查询只按 return_id 等值过滤。 */
    List<OrderRefundEntity> listByReturnIds(@Param("returnIds") List<Long> returnIds);
}
