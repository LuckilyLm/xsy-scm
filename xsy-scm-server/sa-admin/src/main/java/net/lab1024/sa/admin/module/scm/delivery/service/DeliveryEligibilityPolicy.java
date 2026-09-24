package net.lab1024.sa.admin.module.scm.delivery.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.delivery.dao.DeliveryQueryDao;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单能否进入配送候选的唯一判据。
 *
 * <p>P1 分拣落地后，资格不再是「订单已确认」一条，而是
 * {@code CONFIRMED} ∧ 「订单每条有效明细行都被<b>已完成</b>的分拣任务覆盖」
 * （裁决第 11 条与补充第 18 条）。选「按任务状态判定」而不是「按明细上有没有结果判定」，
 * 是为了让重开只改任务状态就能让订单不合格，不必清空已录入的分拣量与原因。
 *
 * <p>缺货与多拣都算「已处理」：少拣的货不发是本阶段业务上可接受的结果，
 * 部分发货如何结算由 P2 决定，本类不做任何数量层面的判断。
 */
@Component
@RequiredArgsConstructor
public class DeliveryEligibilityPolicy {

    private final DeliveryQueryDao queries;

    public List<String> candidateStatuses() {
        return List.of("CONFIRMED");
    }

    /**
     * 判据必须在<b>已加锁</b>的订单上调用：调用方先 {@code orders.lock(id)} 再问这里，
     * 否则「读明细覆盖」与「写线路占用」之间存在重开/取消的竞态窗口。
     */
    public boolean eligible(SalesOrderEntity order) {
        return order != null && !Boolean.TRUE.equals(order.getDeleted())
                && candidateStatuses().contains(order.getStatus())
                && queries.unsortedItemCount(order.getId()) == 0;
    }
}
