package net.lab1024.sa.admin.module.scm.dashboard.constant;

import java.util.List;

/**
 * 业务待办卡片定义：入口权限 {@code scm:todo:query} 只授权访问待办接口本身，
 * 每张卡片的可见性由「待办权限 ∩ 本卡片所需领域权限」决定。
 *
 * <p>{@code allPerms} 必须全部持有，{@code anyPerms} 非空时至少要持有一项；
 * 计数口径与所列权限对应的领域列表一致（复用其查询服务，不另写一套统计）。
 */
public enum ScmTodoCardEnum {

    INVENTORY_WARNING("inventory-warning", "库存异常", "/inventory/inventory-warning-list",
            List.of("scm:inventory:warning:query"), List.of()),

    RECEIPT_PUTAWAY("receipt-putaway", "待仓库确认入库",
            "/purchase/purchase-receipt-list?status=CONFIRMED&receiptMode=WAREHOUSE_CONFIRM&putawayStatus=PENDING",
            List.of("scm:purchase:receipt:query", "scm:purchase:receipt:putaway"), List.of()),

    LOSS_GAIN_AUDIT("loss-gain-audit", "待审批报损报溢", "/inventory/inventory-loss-gain-list?status=PENDING",
            List.of("scm:inventory:loss-gain:query"),
            List.of("scm:inventory:loss-gain:approve", "scm:inventory:loss-gain:reject")),

    DELIVERY_ROUTE_DRAFT("delivery-route-draft", "草稿配送线路", "/delivery/routes?status=DRAFT",
            List.of("scm:delivery:route:query", "scm:delivery:route:plan"), List.of()),
    ;

    private final String key;
    private final String label;
    private final String route;
    private final List<String> allPerms;
    private final List<String> anyPerms;

    ScmTodoCardEnum(String key, String label, String route, List<String> allPerms, List<String> anyPerms) {
        this.key = key;
        this.label = label;
        this.route = route;
        this.allPerms = allPerms;
        this.anyPerms = anyPerms;
    }

    /**
     * 当前权限集合是否足以看到本卡片（不代表能执行动作，动作仍由原接口鉴权）。
     */
    public boolean visibleTo(List<String> heldPermissions) {
        return heldPermissions.containsAll(allPerms)
                && (anyPerms.isEmpty() || anyPerms.stream().anyMatch(heldPermissions::contains));
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getRoute() {
        return route;
    }
}
