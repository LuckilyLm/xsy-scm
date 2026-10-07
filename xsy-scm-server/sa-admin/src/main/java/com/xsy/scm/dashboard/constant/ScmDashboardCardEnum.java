package com.xsy.scm.dashboard.constant;

import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.purchase.permission.PurchasePermission;

import java.util.List;

/**
 * 首页 KPI 卡片定义：入口权限 {@code scm:dashboard:query} 只授权访问工作台接口本身，每张卡片的可见性由 「入口权限 ∩ 本卡片所需领域权限」决定 ——
 * 与业务待办同范式，不因为用户有入口权限就把五张卡全给他。
 *
 * <p>
 * 卡片只声明业务语义（{@code key} / 所需领域权限 / 单位 / 跳哪里）。中文文案、图标与配色由前端决定， 后端不把 UI 设计耦合进 Java。
 *
 * <p>
 * <b>跳转目标必须是与工作台同一套数据范围的页面</b>：工作台按「归属 ∩ 仓库」收窄，所以「今日销售额」跳订单列表而不是销售报表 —— 报表页的范围策略不同（见 {@code home-workbench-design.md}
 * 的「口径同源 ≠ 范围同源」）， 从工作台点过去会看到另一个数。
 */
public enum ScmDashboardCardEnum {

    /** 今日销售额（确认口径）。 */
    SALES_AMOUNT("sales-amount", OrderPermission.QUERY, "CNY", "/order/order-list"),

    /** 今日订单（确认口径）。 */
    ORDER_COUNT("order-count", OrderPermission.QUERY, "COUNT", "/order/order-list"),

    /** 今日采购额（提交口径）。 */
    PURCHASE_AMOUNT("purchase-amount", PurchasePermission.QUERY, "CNY", "/purchase/purchase-order-list"),

    /** 今日收货单（确认口径）。 */
    RECEIPT_COUNT("receipt-count", PurchasePermission.RECEIPT_QUERY, "COUNT", "/purchase/purchase-receipt-list"),

    /**
     * 库存异常：缺货 + 低于下限 + 高于上限。
     *
     * <p>
     * 三个档位都要人处理，所以合成一个数；「未配置阈值」不算异常（它表示还没配阈值，不是库存出了问题）。
     */
    INVENTORY_ALERT("inventory-alert", InventoryPermission.WARNING_QUERY, "COUNT", "/inventory/inventory-warning-list");

    private final String key;
    private final String queryPermission;
    private final String unit;
    private final String route;

    ScmDashboardCardEnum(String key, String queryPermission, String unit, String route) {
        this.key = key;
        this.queryPermission = queryPermission;
        this.unit = unit;
        this.route = route;
    }

    /** 当前权限集合是否足以看到本卡片（不代表能执行动作，动作仍由各领域接口鉴权）。 */
    public boolean visibleTo(List<String> heldPermissions) {
        return heldPermissions != null && heldPermissions.contains(queryPermission);
    }

    public String getKey() {
        return key;
    }

    public String getQueryPermission() {
        return queryPermission;
    }

    public String getUnit() {
        return unit;
    }

    public String getRoute() {
        return route;
    }
}
