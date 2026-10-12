package com.xsy.scm.delivery.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeliveryRouteOrderDao extends BaseMapper<DeliveryRouteOrderEntity> {

    /**
     * 按订单批量读取「生效中的」线路订单，供其它域只读消费订单级履约事实。
     *
     * <p>
     * 只取 {@code assignment_status = 'ACTIVE'} 的行：取消或移出线路会把旧行改成 {@code RELEASED}， 那是历史分配记录，不代表当前履约进度；同一订单若有多行，只有 ACTIVE
     * 那行是当前事实。
     */
    List<DeliveryRouteOrderEntity> selectActiveByOrderIds(@Param("orderIds") List<Long> orderIds);
}
