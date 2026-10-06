package com.xsy.scm.delivery.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.delivery.dao.DeliveryQueryDao;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单能否进入配送候选的唯一判据。
 *
 * <p>
 * 订单必须处于 {@code CONFIRMED}，且每条有效明细都被<b>已完成</b>的分拣任务覆盖。使用任务状态判定资格，因此重开任务会让订单退出候选池，即使已录入的分拣量与原因仍保留。
 *
 * <p>
 * 完成分拣即可进入配送候选；本类不判断分拣数量或库存数量，发车命令使用分拣实发量并执行库存校验。
 */
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
