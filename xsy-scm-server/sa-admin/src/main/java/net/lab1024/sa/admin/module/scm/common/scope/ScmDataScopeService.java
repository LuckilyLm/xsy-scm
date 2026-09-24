package net.lab1024.sa.admin.module.scm.common.scope;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SCM 数据范围的唯一解析入口（裁决「P0 基线收口裁决」第 2、4 条）。
 *
 * <p>口径：
 * <ul>
 *   <li>功能权限按角色并集，由 Sa-Token 负责；本类只负责「能看哪些行」。</li>
 *   <li>「本人」以业务 owner 字段为准（{@code customer.seller_id} /
 *       {@code sales_order.seller_id} / {@code purchase_order.purchaser_id} /
 *       {@code delivery_driver.employee_id}），{@code created_by} 只是审计字段。</li>
 *   <li>放宽某个维度必须显式授权（{@code *:scope:all:query} 权限或授权行），
 *       没有授权即该维度看不到数据；不存在「参数为 null 就等于全部」。</li>
 *   <li>财务不因是财务而全量：它的仓库范围同样来自 {@code employee_warehouse_scope} 授权行。</li>
 * </ul>
 *
 * <p>取不到登录员工（异步线程、定时任务、直连 Service 的测试）时按失败关闭处理。
 * 需要跨全量跑的内部逻辑应当直接调用未收窄的 Dao，而不是绕过本类去读列表接口。
 */
@Service
@RequiredArgsConstructor
public class ScmDataScopeService {

    /** 查看全部仓库（总部仓管主管、审计类岗位）；否则仓库维度只认授权行。 */
    public static final String WAREHOUSE_ALL_PERM = "scm:inventory:scope:all:query";

    /** 查看全部业务员名下客户（销售主管、财务）。 */
    public static final String CUSTOMER_ALL_PERM = "scm:customer:scope:all:query";

    /** 查看全部业务员名下的销售订单。 */
    public static final String ORDER_ALL_PERM = "scm:order:scope:all:query";

    /** 查看全部采购员的采购单与采购需求。 */
    public static final String PURCHASE_ALL_PERM = "scm:purchase:scope:all:query";

    /** 查看全部司机与线路（调度岗）；普通司机只看绑定到自己名下的线路。 */
    public static final String DELIVERY_ALL_PERM = "scm:delivery:scope:all:query";

    /** 客户业务归属分配/改派权；持有者才能把 {@code seller_id} 写成别人，或看到未分配客户。 */
    public static final String CUSTOMER_ASSIGN_PERM = "scm:customer:assign";

    /** 采购负责人分配/改派权，语义同上。 */
    public static final String PURCHASE_ASSIGN_PERM = "scm:purchase:assign";

    /** 配送订单金额可见权：司机默认隐藏金额，需要时由这个独立权限开放。 */
    public static final String DELIVERY_AMOUNT_PERM = "scm:delivery:amount:query";

    private final ScmDataScopeDao scopeDao;

    /**
     * 解析当前调用者的数据范围。每次请求都重新解析，不缓存在登录态里：
     * 授权行调整后必须立即生效，而 {@code RequestEmployee} 是带 Spring Cache 的登录快照。
     */
    public ScmDataScopeContext resolve() {
        RequestUser requestUser = SmartRequestUtil.getRequestUser();
        if (!(requestUser instanceof RequestEmployee employee) || employee.getEmployeeId() == null) {
            return ScmDataScopeContext.denied();
        }
        Long employeeId = employee.getEmployeeId();
        boolean costVisible = hasPermission(ScmReportAccess.COST_QUERY_PERM);
        if (isAdministrator()) {
            return ScmDataScopeContext.unrestricted(employeeId, costVisible);
        }
        return new ScmDataScopeContextBuilder(employeeId, costVisible)
                .warehouse(hasPermission(WAREHOUSE_ALL_PERM) ? ScmValueScope.all()
                        : ScmValueScope.of(scopeDao.listAuthorizedWarehouseIds(employeeId)))
                .customerSeller(hasPermission(CUSTOMER_ALL_PERM) ? ScmValueScope.all()
                        : ScmValueScope.of(List.of(employeeId)))
                .orderSeller(hasPermission(ORDER_ALL_PERM) ? ScmValueScope.all()
                        : ScmValueScope.of(List.of(employeeId)))
                .purchaser(hasPermission(PURCHASE_ALL_PERM) ? ScmValueScope.all()
                        : ScmValueScope.of(List.of(employeeId)))
                .driver(hasPermission(DELIVERY_ALL_PERM) ? ScmValueScope.all()
                        : ScmValueScope.of(scopeDao.listDriverIdsByEmployee(employeeId)))
                .build();
    }

    /**
     * break-glass 判定的唯一出处：{@code administratorFlag=true} 绕过 SCM 数据范围。
     *
     * <p>维度落在 {@link ScmValueScope} 里的（仓库、负责人、司机）由 {@link #resolve()} 返回
     * {@code all()} 天然放行；**不落在范围值对象里的维度**（例如分拣的「受指派人 = 本人」是
     * 与员工 id 直接比等值）必须显式调用本方法同等放行，否则同一个超管账号会出现
     * 「仓库看得见、人看不见」这种半开半关的口径。
     */
    public static boolean isAdministrator() {
        return SmartRequestUtil.getRequestUser() instanceof RequestEmployee employee
                && Boolean.TRUE.equals(employee.getAdministratorFlag());
    }

    /**
     * 权限判断失败关闭：未登录、Sa-Token 上下文缺失、依赖未装配都按「无权限」处理。
     * 与 {@link ScmReportAccess#canViewCost()} 同一取向——宁可少给数据，不可多给。
     */
    public static boolean hasPermission(String permissionCode) {
        try {
            return StpUtil.hasPermission(permissionCode);
        } catch (RuntimeException notLoggedIn) {
            return false;
        }
    }

    /**
     * 维度为空时的空分页：形状与正常分页一致（{@code total=0}、{@code emptyFlag=true}），
     * 前端不需要为「无授权」写第二套分支，也不会把它误读成「确实没有数据」以外的状态。
     */
    public static <T> PageResult<T> emptyPage(PageParam form) {
        Page<T> page = new Page<>(form.getPageNum(), form.getPageSize());
        page.setTotal(0);
        return SmartPageUtil.convert2PageResult(page, List.of());
    }

    /** 仅供 {@link #resolve()} 组装不可变上下文，避免七参数构造器写错顺序。 */
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
