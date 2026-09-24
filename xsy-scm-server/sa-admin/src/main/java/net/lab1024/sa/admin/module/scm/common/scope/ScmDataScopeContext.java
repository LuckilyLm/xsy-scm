package net.lab1024.sa.admin.module.scm.common.scope;

import lombok.Getter;

/**
 * 某次调用在当前登录员工下的 SCM 数据范围解析结果（不可变值对象）。
 *
 * <p>由 {@link ScmDataScopeService#resolve()} 集中产出，Service 层显式下传给 Mapper；
 * 不使用底座 {@code @DataScope} 插件，理由与口径见
 * {@code docs/decisions.md}「P0 基线收口裁决」第 1–5 条。
 */
@Getter
public final class ScmDataScopeContext {

    /** 当前员工 id；{@link #denied()} 时为 {@code null}。 */
    private final Long employeeId;

    /** 仓库维度：只来自 {@code employee_warehouse_scope} 授权行，不做隐式默认。 */
    private final ScmValueScope warehouseScope;

    /** 客户负责人维度：{@code customer.seller_id}。 */
    private final ScmValueScope customerSellerScope;

    /** 销售订单负责人维度：{@code sales_order.seller_id}。 */
    private final ScmValueScope orderSellerScope;

    /** 采购负责人维度：{@code purchase_order.purchaser_id} / {@code purchase_demand.purchaser_id}。 */
    private final ScmValueScope purchaserScope;

    /** 司机维度：{@code delivery_route.driver_id}，经 {@code delivery_driver.employee_id} 反查。 */
    private final ScmValueScope driverScope;

    /** 成本字段是否可见；沿用 V50 已种下的独立成本权限，缺省时由调用方抹成 null 而非 0。 */
    private final boolean costVisible;

    ScmDataScopeContext(Long employeeId, ScmValueScope warehouseScope, ScmValueScope customerSellerScope,
                        ScmValueScope orderSellerScope, ScmValueScope purchaserScope,
                        ScmValueScope driverScope, boolean costVisible) {
        this.employeeId = employeeId;
        this.warehouseScope = warehouseScope;
        this.customerSellerScope = customerSellerScope;
        this.orderSellerScope = orderSellerScope;
        this.purchaserScope = purchaserScope;
        this.driverScope = driverScope;
        this.costVisible = costVisible;
    }

    /**
     * break-glass：{@code administratorFlag=true} 与既有底座语义一致，绕过数据范围。
     * 正式业务角色的验收必须用 {@code administratorFlag=false} 的账号，超管通过不算证据。
     */
    static ScmDataScopeContext unrestricted(Long employeeId, boolean costVisible) {
        return new ScmDataScopeContext(employeeId, ScmValueScope.all(), ScmValueScope.all(), ScmValueScope.all(),
                ScmValueScope.all(), ScmValueScope.all(), costVisible);
    }

    /**
     * 取不到登录员工时的结果：所有维度都无授权。SCM 读接口不应在无身份上下文里被调用，
     * 真出现了按「什么都看不到」处理，绝不回退成全量。
     */
    static ScmDataScopeContext denied() {
        return new ScmDataScopeContext(null, ScmValueScope.none(), ScmValueScope.none(), ScmValueScope.none(),
                ScmValueScope.none(), ScmValueScope.none(), false);
    }

    /**
     * 便捷入口：本上下文里「按仓库收窄」的维度（库存族、收货、出库、调拨）是否必然 0 行，
     * 调用方据此短路成空分页，不必让数据库跑一次恒假谓词。
     */
    public boolean warehouseNowhere() {
        return warehouseScope.isEmpty();
    }
}
