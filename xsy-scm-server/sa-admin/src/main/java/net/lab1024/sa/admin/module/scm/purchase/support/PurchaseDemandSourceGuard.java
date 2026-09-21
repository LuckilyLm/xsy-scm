package net.lab1024.sa.admin.module.scm.purchase.support;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderEntity;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_SOURCE_INVALID;

/**
 * 采购需求的**来源合法性**守卫（W5 Target Design §7.4 第 3 步 / §7.7 的 40980）。
 *
 * <p>为什么需要它：`purchase_demand` 的来源唯一索引
 * （`uk_purchase_demand_source_active`）只保证「一个销售订单行只产生一条活动需求」，
 * **不保证来源订单处于可采购状态**。需求一旦生成，`required_quantity` 会被后续分配
 * 长期引用；若来源订单尚未确认（`DRAFT`）或被取消，就会产生「采购了不该采购的东西」。
 * 数据库层没有外键（`AGENTS.md` 明文禁止），因此这条不变量只能由服务层承担 ——
 * 本类就是那个唯一入口。
 *
 * <p><b>实现为纯函数</b>：只依赖传入的实体，不注入 DAO、不开事务。这样
 * 「已取消订单不得生成需求」可以在单测里毫秒级断言，也便于 `generate` 在
 * 逐行循环中复用（不产生 N 次查询）。
 *
 * <p><b>为什么不用 SQL 里已有的 {@code o.status = 'CONFIRMED'} 过滤</b>：那是
 * `generate` 的**取数口径**（决定「取哪些行」），本类是**不变量断言**（决定「取到的行是否可信」）。
 * 两者是不同层次的防线：口径可能因新入口（按订单 id 定向生成、补生成、数据修复脚本）
 * 而被放宽，而不变量不允许被放宽。若只有 SQL 过滤，40980 将永远不可达。
 */
public final class PurchaseDemandSourceGuard {

    private PurchaseDemandSourceGuard() {
    }

    /**
     * 销售订单的「可采购」状态，与 W4 的 `ScmOrderStatusEnum.CONFIRMED` 对齐。
     */
    public static final String CONFIRMED = "CONFIRMED";

    /**
     * 断言来源销售订单可产生采购需求。
     *
     * <p>拒绝条件（全部 → 40980）：
     * <ul>
     *   <li>来源订单缺失（{@code null}）；</li>
     *   <li>来源订单已软删（{@code deleted == true}）；</li>
     *   <li>来源订单状态不是 {@code CONFIRMED}
     *       （`DRAFT` / `PENDING` 尚未确认，`CANCELLED` 已作废）。</li>
     * </ul>
     *
     * @throws ScmBusinessException 40980
     */
    public static void requireConfirmed(SalesOrderEntity order) {
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            throw new ScmBusinessException(PURCHASE_DEMAND_SOURCE_INVALID);
        }
        if (!CONFIRMED.equals(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_DEMAND_SOURCE_INVALID);
        }
    }

    /**
     * 布尔形态，便于 `generate` 在流式过滤中复用（不抛异常）。
     */
    public static boolean isConfirmed(SalesOrderEntity order) {
        return order != null
                && !Boolean.TRUE.equals(order.getDeleted())
                && CONFIRMED.equals(order.getStatus());
    }
}
