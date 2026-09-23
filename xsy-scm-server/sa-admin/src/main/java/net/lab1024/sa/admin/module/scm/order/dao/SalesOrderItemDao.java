package net.lab1024.sa.admin.module.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderRecentPriceVO;

@Mapper
public interface SalesOrderItemDao extends BaseMapper<SalesOrderItemEntity> {
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    SalesOrderItemEntity lock(@Param("id") Long id);

    List<SalesOrderItemEntity> list(@Param("id") Long id);

    /**
     * 某客户某 SKU 的最近已确认订单价（Wave 3 §7.5，只读）：只取 CONFIRMED 订单行的锁定单价快照，
     * 按 {@code confirmed_at DESC, order_id DESC} 倒序，{@code limit} 约束的是最近 N 张订单而非行数。
     */
    List<OrderRecentPriceVO> recentPrices(@Param("customerId") Long customerId,
                                          @Param("skuId") Long skuId,
                                          @Param("limit") int limit);
}
