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
 * 统一解析数据可见范围，功能权限交给 Sa-Token。行权限看业务负责人和授权仓库，不看创建人。认不出登录员工或没有授权就拒绝；财务人员也不会自动拥有全部仓库。
 */
@Service
@RequiredArgsConstructor
public class ScmDataScopeService {

    /** 查看全部仓库；否则仓库维度只认授权行。 */
    public static final String WAREHOUSE_ALL_PERM = ScmCrossDomainPermission.INVENTORY_SCOPE_ALL_QUERY;

    /** 查看全部业务员名下的客户。 */
    public static final String CUSTOMER_ALL_PERM = ScmCrossDomainPermission.CUSTOMER_SCOPE_ALL_QUERY;

    /** 查看全部业务员名下的销售订单。 */
    public static final String ORDER_ALL_PERM = ScmCrossDomainPermission.ORDER_SCOPE_ALL_QUERY;

    /** 查看全部采购员的采购单与采购需求。 */
    public static final String PURCHASE_ALL_PERM = ScmCrossDomainPermission.PURCHASE_SCOPE_ALL_QUERY;

    /** 查看全部司机与线路；普通司机只看绑定到自己名下的线路。 */
    public static final String DELIVERY_ALL_PERM = ScmCrossDomainPermission.DELIVERY_SCOPE_ALL_QUERY;

    /** 客户改派权：有它才能把 {@code seller_id} 写成别人，或看到未分配的客户。 */
    public static final String CUSTOMER_ASSIGN_PERM = ScmCrossDomainPermission.CUSTOMER_ASSIGN;

    /** 采购负责人分配/改派权，规则同上。 */
    public static final String PURCHASE_ASSIGN_PERM = ScmCrossDomainPermission.PURCHASE_ASSIGN;

    /** 配送订单金额可见权：司机默认看不到金额，需要时单独开放。 */
    public static final String DELIVERY_AMOUNT_PERM = ScmCrossDomainPermission.DELIVERY_AMOUNT_QUERY;

    private final ScmDataScopeDao dataScopeDao;

    /**
     * 解析当前调用者的数据范围。每次请求重新解析，不缓存在登录态里：授权调整后要立即生效。
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
     * 超级管理员不受业务数据范围限制，仓库和负责人范围全部放行；分拣指派等额外维度还要单独判断。
     */
    public static boolean isAdministrator() {
        return SmartRequestUtil.getRequestUser() instanceof RequestEmployee employee
            && Boolean.TRUE.equals(employee.getAdministratorFlag());
    }

    /**
     * 权限判断失败就按无权限处理：未登录、Sa-Token 上下文缺失、依赖没装配都算。
     */
    public static boolean hasPermission(String permissionCode) {
        try {
            return StpUtil.hasPermission(permissionCode);
        } catch (RuntimeException notLoggedIn) {
            return false;
        }
    }

    /**
     * 无授权时的空分页：形状与正常分页一致（{@code total=0}、{@code emptyFlag=true}），前端不用另写分支。
     */
    public static <T> PageResult<T> emptyPage(PageParam form) {
        Page<T> page = new Page<>(form.getPageNum(), form.getPageSize());
        page.setTotal(0);
        return SmartPageUtil.convert2PageResult(page, List.of());
    }

    /** 仅供 {@link #resolve()} 组装上下文，避免七参数构造器写错顺序。 */
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
