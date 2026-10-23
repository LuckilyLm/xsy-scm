package com.xsy.scm.common.scope;

import lombok.Getter;

/**
 * 当前登录员工的数据范围解析结果（不可变值对象）。
 *
 * <p>
 * 由 {@link ScmDataScopeService#resolve()} 集中产出，Service 层显式下传给 Mapper；不用底座的 {@code @DataScope} 插件，避免空范围被当成全量数据。
 */
@Getter
public final class ScmDataScopeContext {

    /** 当前员工 id；{@link #denied()} 时为 {@code null}。 */
    private final Long employeeId;

    /** 仓库维度：只来自 {@code xsy_employee_warehouse_scope} 授权行。 */
    private final ScmValueScope warehouseScope;

    /** 客户负责人维度：{@code xsy_customer.seller_id}。 */
    private final ScmValueScope customerSellerScope;

    /** 销售订单负责人维度：{@code xsy_sales_order.seller_id}。 */
    private final ScmValueScope orderSellerScope;

    /** 采购负责人维度：{@code xsy_purchase_order.purchaser_id} / {@code xsy_purchase_demand.purchaser_id}。 */
    private final ScmValueScope purchaserScope;

    /** 司机维度：{@code xsy_delivery_route.driver_id}，经 {@code xsy_delivery_driver.employee_id} 反查。 */
    private final ScmValueScope driverScope;

    /** 成本字段是否可见；没有成本权限时由调用方输出 null 而不是 0。 */
    private final boolean costVisible;

    ScmDataScopeContext(Long employeeId, ScmValueScope warehouseScope, ScmValueScope customerSellerScope,
        ScmValueScope orderSellerScope, ScmValueScope purchaserScope, ScmValueScope driverScope, boolean costVisible) {
        this.employeeId = employeeId;
        this.warehouseScope = warehouseScope;
        this.customerSellerScope = customerSellerScope;
        this.orderSellerScope = orderSellerScope;
        this.purchaserScope = purchaserScope;
        this.driverScope = driverScope;
        this.costVisible = costVisible;
    }

    /**
     * 超管（{@code administratorFlag=true}）不受数据范围限制。验收业务角色时要用非超管账号。
     */
    static ScmDataScopeContext unrestricted(Long employeeId, boolean costVisible) {
        return new ScmDataScopeContext(employeeId, ScmValueScope.all(), ScmValueScope.all(), ScmValueScope.all(),
            ScmValueScope.all(), ScmValueScope.all(), costVisible);
    }

    /**
     * 取不到登录员工时的结果：所有维度都无授权。宁可什么都看不到，也不回退成全量。
     */
    static ScmDataScopeContext denied() {
        return new ScmDataScopeContext(null, ScmValueScope.none(), ScmValueScope.none(), ScmValueScope.none(),
            ScmValueScope.none(), ScmValueScope.none(), false);
    }

    /**
     * 按仓库收窄的查询是否必然 0 行，调用方据此直接返回空分页。
     */
    public boolean warehouseNowhere() {
        return warehouseScope.isEmpty();
    }
}
