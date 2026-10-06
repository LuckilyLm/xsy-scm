package com.xsy.scm.purchase.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_SOURCE_INVALID;

/**
 * 采购需求来源不变量：只有未删除且已确认（{@code CONFIRMED}）的销售订单才能生成采购需求。
 *
 * <p>
 * {@code uk_purchase_demand_source_active} 只保证「一个销售订单行只产生一条活动需求」，不保证来源订单处于可采购状态；数据库层没有外键，因此这条不变量只能由服务层承担，本类即唯一入口 ——
 * 独立于查询 SQL 的取数口径（{@code generate} 只查已确认订单），不变量断言不允许被新入口放宽，否则 40980 永不可达。
 */
public final class PurchaseDemandSourceGuard {

    private PurchaseDemandSourceGuard() {
    }

    /**
     * 断言来源销售订单可产生采购需求。
     *
     * <p>
     * 拒绝条件（全部 → 40980）：
     * <ul>
     * <li>来源订单缺失（{@code null}）；</li>
     * <li>来源订单已软删（{@code deleted == true}）；</li>
     * <li>来源订单状态不是 {@code CONFIRMED} （{@code DRAFT} / {@code PENDING} 尚未确认，{@code CANCELLED} 已作废）。</li>
     * </ul>
     *
     * @throws ScmBusinessException
     *             40980
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
