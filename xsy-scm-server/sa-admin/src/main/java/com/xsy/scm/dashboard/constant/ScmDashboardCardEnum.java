package com.xsy.scm.dashboard.constant;

import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.purchase.permission.PurchasePermission;

import java.util.List;

/**
 * 首页每张指标卡片需同时满足工作台入口权限和对应业务权限。未授权的卡片不返回。
 */
public enum ScmDashboardCardEnum {

    /**
     * 今日销售额（确认口径）。
     */
    SALES_AMOUNT("sales-amount", OrderPermission.QUERY, ScmDashboardValueType.CNY, "/order/order-list",
        ScmDashboardCardGroup.SALES),

    /**
     * 今日订单（确认口径）。
     */
    ORDER_COUNT("order-count", OrderPermission.QUERY, ScmDashboardValueType.COUNT, "/order/order-list",
        ScmDashboardCardGroup.SALES),

    /**
     * 今日采购额（提交口径）。
     */
    PURCHASE_AMOUNT("purchase-amount", PurchasePermission.QUERY, ScmDashboardValueType.CNY,
        "/purchase/purchase-order-list", ScmDashboardCardGroup.PURCHASE),

    /**
     * 今日收货单（确认口径）。
     */
    RECEIPT_COUNT("receipt-count", PurchasePermission.RECEIPT_QUERY, ScmDashboardValueType.COUNT,
        "/purchase/purchase-receipt-list", ScmDashboardCardGroup.PURCHASE),

    /**
     * 首页库存预警数量与库存预警列表的筛选范围一致，不直接用更宽的库存健康度分档数量代替。
     */
    INVENTORY_WARNING("inventory-warning", InventoryPermission.WARNING_QUERY, ScmDashboardValueType.COUNT,
        "/inventory/inventory-warning-list", ScmDashboardCardGroup.INVENTORY_WARNING);

    private final String key;
    private final String queryPermission;
    private final ScmDashboardValueType unit;
    private final String route;
    private final ScmDashboardCardGroup group;

    ScmDashboardCardEnum(String key, String queryPermission, ScmDashboardValueType unit, String route,
        ScmDashboardCardGroup group) {
        this.key = key;
        this.queryPermission = queryPermission;
        this.unit = unit;
        this.route = route;
        this.group = group;
    }

    /**
     * 当前权限集合是否足以看到本卡片（不代表能执行动作，动作仍由各领域接口鉴权）。
     */
    public boolean visibleTo(List<String> heldPermissions) {
        return heldPermissions != null && heldPermissions.contains(queryPermission);
    }

    public String getKey() {
        return key;
    }

    public String getQueryPermission() {
        return queryPermission;
    }

    public ScmDashboardValueType getUnit() {
        return unit;
    }

    public String getRoute() {
        return route;
    }

    /**
     * 本卡片出自哪一组聚合，供取数裁剪使用。
     */
    public ScmDashboardCardGroup group() {
        return group;
    }
}
