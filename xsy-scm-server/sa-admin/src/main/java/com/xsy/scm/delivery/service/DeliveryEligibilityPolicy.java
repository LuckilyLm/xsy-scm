package com.xsy.scm.delivery.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.delivery.dao.DeliveryQueryDao;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/** 进入配送候选的订单必须已确认，所有有效明细均完成分拣。候选资格不等于库存足够，发车时仍要校验实发量和余额。 */
@Component
@RequiredArgsConstructor
public class DeliveryEligibilityPolicy {

    private final DeliveryQueryDao deliveryQueryDao;

    public List<String> candidateStatuses() {
        return List.of(ScmOrderStatusEnum.CONFIRMED.name());
    }

    /**
     * 判据必须在<b>已加锁</b>的订单上调用：调用方先 {@code orders.lock(id)} 再问这里，否则「读明细覆盖」与「写线路占用」之间存在重开/取消的竞态窗口。
     */
    public boolean eligible(SalesOrderEntity order) {
        return order != null && !Boolean.TRUE.equals(order.getDeleted())
                && candidateStatuses().contains(order.getStatus())
                && deliveryQueryDao.unsortedItemCount(order.getId()) == 0;
    }
}
