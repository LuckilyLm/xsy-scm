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
    SALES_AMOUNT("sales-amount", OrderPermission.QUERY, ScmDashboardValueType.CNY, "/order/order-list",
            ScmDashboardCardGroup.SALES),

    /** 今日订单（确认口径）。 */
    ORDER_COUNT("order-count", OrderPermission.QUERY, ScmDashboardValueType.COUNT, "/order/order-list",
            ScmDashboardCardGroup.SALES),

    /** 今日采购额（提交口径）。 */
    PURCHASE_AMOUNT("purchase-amount", PurchasePermission.QUERY, ScmDashboardValueType.CNY,
            "/purchase/purchase-order-list", ScmDashboardCardGroup.PURCHASE),

    /** 今日收货单（确认口径）。 */
    RECEIPT_COUNT("receipt-count", PurchasePermission.RECEIPT_QUERY, ScmDashboardValueType.COUNT,
            "/purchase/purchase-receipt-list", ScmDashboardCardGroup.PURCHASE),

    /**
     * 库存预警：取值就是库存预警列表的分页总数。
     *
     * <p>
     * <b>刻意不用指标层的健康度分档求和</b>：那张分档表更宽（含缺货与未配置阈值），而预警列表默认只收「低于下限 / 高于上限」 两档。用分档求和当卡片数字会出现「首页 12、点进去只有 8」——
     * 数字与明细对不上，比数字本身更伤信任。 分档数据另走 {@code /scm/dashboard/inventory-health}，供首页的库存健康卡使用。
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

    public ScmDashboardValueType getUnit() {
        return unit;
    }

    public String getRoute() {
        return route;
    }

    /** 本卡片出自哪一组聚合，供取数裁剪使用。 */
    public ScmDashboardCardGroup group() {
        return group;
    }
}
