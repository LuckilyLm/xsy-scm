package com.xsy.scm.purchase.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_SOURCE_INVALID;

/**
 * 采购需求来源约束：只有未删除且 {@code CONFIRMED} 的销售订单可以生成采购需求。
 *
 * <p>
 * {@code uk_purchase_demand_source_active} 只约束「一个销售订单行只产生一条活动需求」，不约束来源订单状态；数据库层也没有外键。 这条不变量独立于查询 SQL
 * 的取数口径，由本类对所有生成入口统一承担。
 */
public final class PurchaseDemandSourceGuard {

    private PurchaseDemandSourceGuard() {
    }

    /**
     * 断言来源销售订单可产生采购需求；来源缺失、已软删或状态不是 {@code CONFIRMED} 时抛 40980。
     */
    public static void requireConfirmed(SalesOrderEntity order) {
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            throw new ScmBusinessException(PURCHASE_DEMAND_SOURCE_INVALID);
        }
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_DEMAND_SOURCE_INVALID);
        }
    }

    /**
     * 布尔形态，便于 {@code generate} 在流式过滤中复用（不抛异常）。
     */
    public static boolean isConfirmed(SalesOrderEntity order) {
        return order != null && !Boolean.TRUE.equals(order.getDeleted())
                && ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus());
    }
}
