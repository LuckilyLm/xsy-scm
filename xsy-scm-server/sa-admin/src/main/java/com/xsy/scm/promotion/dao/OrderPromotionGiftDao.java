package com.xsy.scm.promotion.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.promotion.domain.entity.OrderPromotionGiftEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 满赠赠品权益读写。
 *
 * <p>
 * 没有更新与删除：表上的触发器会拒绝改动权益内容，接口层也不提供 —— 能改就等于发货时可以换赠品。
 * 需要纠正时追加新的事实（当前无此场景），而不是改这一行。
 */
@Mapper
public interface OrderPromotionGiftDao extends BaseMapper<OrderPromotionGiftEntity> {

    /** 某订单冻结的全部赠品权益，按活动 id 升序（同一订单每个活动至多一行）。 */
    List<OrderPromotionGiftEntity> listByOrder(@Param("salesOrderId") Long salesOrderId);

    int insertGift(@Param("row") OrderPromotionGiftEntity row);
}
