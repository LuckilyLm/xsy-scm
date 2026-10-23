package com.xsy.scm.delivery.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;
import com.xsy.scm.delivery.domain.vo.DeliveryOrderProgressVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeliveryRouteOrderDao extends BaseMapper<DeliveryRouteOrderEntity> {

    /**
     * 按订单批量读取「生效中的」线路订单，供其它域只读消费订单级履约状态。
     *
     * <p>
     * 只取 {@code assignment_status = 'ACTIVE'} 的行：取消或移出线路会把旧行改成 {@code RELEASED}，那是历史分配，不代表当前进度。同一订单有多行时，只有 ACTIVE
     * 那行是当前状态。
     */
    List<DeliveryRouteOrderEntity> selectActiveByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 按订单批量读取配送进度（线路信息 + 订单级履约状态），供客户侧展示配送详情与时间线。
     *
     * <p>
     * 规则同上：只取 ACTIVE 关联。线路已逻辑删除时整行不返回，调用方按「没有配送信息」处理，别拿半条线路编时间线。
     */
    List<DeliveryOrderProgressVO> selectProgressByOrderIds(@Param("orderIds") List<Long> orderIds);
}
