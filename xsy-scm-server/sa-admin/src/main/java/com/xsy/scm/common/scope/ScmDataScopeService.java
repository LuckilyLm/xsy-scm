package com.xsy.scm.common.scope;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.permission.ScmCrossDomainPermission;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一解析 SCM 的数据可见范围；功能权限由 Sa-Token 处理。 行权限按业务负责人及授权仓库判断，不按创建人判断。 没有授权或无法识别登录员工时拒绝访问；财务人员也不会自动获得全部仓库权限。
 */
@Service
@RequiredArgsConstructor
public class ScmDataScopeService {

    /**
     * 查看全部仓库（总部仓管主管、审计类岗位）；否则仓库维度只认授权行。
     */
    public static final String WAREHOUSE_ALL_PERM = ScmCrossDomainPermission.INVENTORY_SCOPE_ALL_QUERY;

    /**
     * 查看全部业务员名下客户（销售主管、财务）。
     */
    public static final String CUSTOMER_ALL_PERM = ScmCrossDomainPermission.CUSTOMER_SCOPE_ALL_QUERY;

    /**
     * 查看全部业务员名下的销售订单。
     */
    public static final String ORDER_ALL_PERM = ScmCrossDomainPermission.ORDER_SCOPE_ALL_QUERY;

    /**
     * 查看全部采购员的采购单与采购需求。
     */
    public static final String PURCHASE_ALL_PERM = ScmCrossDomainPermission.PURCHASE_SCOPE_ALL_QUERY;

    /**
     * 查看全部司机与线路（调度岗）；普通司机只看绑定到自己名下的线路。
     */
    public static final String DELIVERY_ALL_PERM = ScmCrossDomainPermission.DELIVERY_SCOPE_ALL_QUERY;

    /**
     * 客户业务归属分配/改派权；持有者才能把 {@code seller_id} 写成别人，或看到未分配客户。
     */
    public static final String CUSTOMER_ASSIGN_PERM = ScmCrossDomainPermission.CUSTOMER_ASSIGN;

    /**
     * 采购负责人分配/改派权，语义同上。
     */
    public static final String PURCHASE_ASSIGN_PERM = ScmCrossDomainPermission.PURCHASE_ASSIGN;

    /**
     * 配送订单金额可见权：司机默认隐藏金额，需要时由这个独立权限开放。
     */
    public static final String DELIVERY_AMOUNT_PERM = ScmCrossDomainPermission.DELIVERY_AMOUNT_QUERY;

    private final ScmDataScopeDao dataScopeDao;

    /**
     * 解析当前调用者的数据范围。每次请求都重新解析，不缓存在登录态里：授权行调整后必须立即生效，而 {@code RequestEmployee} 是带 Spring Cache 的登录快照。
     */
    public ScmDataScopeContext resolve() {

        RequestUser requestUser = SmartRequestUtil.getRequestUser();

        if (!(requestUser instanceof RequestEmployee employee) || employee.getEmployeeId() == null) {
            return ScmDataScopeContext.denied();
        }

        Long employeeId = employee.getEmployeeId();
        boolean costVisible = hasPermission(ScmCrossDomainPermission.REPORT_COST_QUERY);

        if (isAdministrator()) {
            return ScmDataScopeContext.unrestricted(employeeId, costVisible);
        }

        return new ScmDataScopeContextBuilder(employeeId, costVisible)
            .warehouse(hasPermission(WAREHOUSE_ALL_PERM)
                ? ScmValueScope.all()
                : ScmValueScope.of(dataScopeDao.listAuthorizedWarehouseIds(employeeId)))
            .customerSeller(
                hasPermission(CUSTOMER_ALL_PERM) ? ScmValueScope.all() : ScmValueScope.of(List.of(employeeId)))
            .orderSeller(hasPermission(ORDER_ALL_PERM) ? ScmValueScope.all() : ScmValueScope.of(List.of(employeeId)))
            .purchaser(hasPermission(PURCHASE_ALL_PERM) ? ScmValueScope.all() : ScmValueScope.of(List.of(employeeId)))
            .driver(hasPermission(DELIVERY_ALL_PERM)
                ? ScmValueScope.all()
                : ScmValueScope.of(dataScopeDao.listDriverIdsByEmployee(employeeId)))
            .build();
    }

    /**
     * 超级管理员可以绕过 SCM 的业务数据范围，仓库和负责人范围统一放行；分拣指派等额外维度仍需显式检查超级管理员。
     */
    public static boolean isAdministrator() {
        return SmartRequestUtil.getRequestUser() instanceof RequestEmployee employee
            && Boolean.TRUE.equals(employee.getAdministratorFlag());
    }

    /**
     * 权限判断失败关闭：未登录、Sa-Token 上下文缺失、依赖未装配都按「无权限」处理，避免泄露受限数据。
     */
    public static boolean hasPermission(String permissionCode) {
        try {
            return StpUtil.hasPermission(permissionCode);
        } catch (RuntimeException notLoggedIn) {
            return false;
        }
    }

    /**
     * 维度为空时的空分页：形状与正常分页一致（{@code total=0}、{@code emptyFlag=true}），前端不需要为「无授权」写第二套分支，也不会把它误读成「确实没有数据」以外的状态。
     */
    public static <T> PageResult<T> emptyPage(PageParam form) {
        Page<T> page = new Page<>(form.getPageNum(), form.getPageSize());
        page.setTotal(0);
        return SmartPageUtil.convert2PageResult(page, List.of());
    }

    /**
     * 仅供 {@link #resolve()} 组装不可变上下文，避免七参数构造器写错顺序。
     */
    private static final class ScmDataScopeContextBuilder {

        private final Long employeeId;
        private final boolean costVisible;
        private ScmValueScope warehouse = ScmValueScope.none();
        private ScmValueScope customerSeller = ScmValueScope.none();
        private ScmValueScope orderSeller = ScmValueScope.none();
        private ScmValueScope purchaser = ScmValueScope.none();
        private ScmValueScope driver = ScmValueScope.none();

        private ScmDataScopeContextBuilder(Long employeeId, boolean costVisible) {
            this.employeeId = employeeId;
            this.costVisible = costVisible;
        }

        private ScmDataScopeContextBuilder warehouse(ScmValueScope scope) {
            this.warehouse = scope;
            return this;
        }

        private ScmDataScopeContextBuilder customerSeller(ScmValueScope scope) {
            this.customerSeller = scope;
            return this;
        }

        private ScmDataScopeContextBuilder orderSeller(ScmValueScope scope) {
            this.orderSeller = scope;
            return this;
        }

        private ScmDataScopeContextBuilder purchaser(ScmValueScope scope) {
            this.purchaser = scope;
            return this;
        }

        private ScmDataScopeContextBuilder driver(ScmValueScope scope) {
            this.driver = scope;
            return this;
        }

        private ScmDataScopeContext build() {
            return new ScmDataScopeContext(employeeId, warehouse, customerSeller, orderSeller, purchaser, driver,
                costVisible);
        }
    }
}
