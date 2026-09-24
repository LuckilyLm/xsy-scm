package net.lab1024.sa.admin.module.scm.warehouse;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import cn.dev33.satoken.stp.StpUtil;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseOrderService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseQueryForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseVO;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseQueryService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * 仓库主档的可见性收口（P0-H 裁决第 3 条）。
 *
 * <p>裁决口径：非管理员调用仓库接口时<b>只返回自己被授权的仓库</b>，未授权仓的
 * id / 名称 / 地址一律不给 —— 选择器一旦漏出别人的仓库 id，就等于把「往那个仓提交单据」
 * 的入口也一并给了出去，而那正是 {@code ScmWarehouseScopeGuard} 在写侧要挡的东西。
 *
 * <p><b>同时钉住一条不能收的</b>：历史单据展示的是自己行上的
 * {@code warehouse_name_snapshot}，不经过仓库主档读取。所以「已经无权管这个仓」的员工
 * 仍然能读到自己当年那张单的收货仓名字 —— 那是历史事实，不等于获得该仓的查询权。
 * 本类最后一条用例把这两件事放在同一个人身上取证：选择器为空，快照照旧可读。
 *
 * <p>身份与权限的造法与 {@code ScmPurchaseDataScopePgIT} 一致：登录员工走
 * {@code SmartRequestUtil}，功能权限走 {@code mockStatic(StpUtil.class)}，
 * 未点名的权限码取 Mockito 默认值 false（即失败关闭），测试员工一律 {@code administratorFlag=false}。
 */
@DisplayName("P0-H 仓库主档可见性（PG IT）")
class WarehouseDataScopePgIT extends ScmW5PgITBase {

    @Autowired
    private WarehouseQueryService warehouseQueryService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseQueryService purchaseQueryService;

    @Test
    @DisplayName("选择器只返回授权仓：单仓授权看不到别的仓，全量范围授权才返回全部")
    void selectorReturnsOnlyAuthorizedWarehouses() {
        Long warehouseA = newWarehouse("SA");
        Long warehouseB = newWarehouse("SB");
        Long onlyA = scopedEmployee(warehouseA);
        Long none = newEmployee("NONE");

        assertThat(as(onlyA, Set.of(), this::selectorCodes))
                .as("只有一个仓的授权，就不该出现第二个仓")
                .containsExactly(warehouseA);
        assertThat(as(none, Set.of(), this::selectorCodes))
                .as("没有任何授权行时是空列表，不是全量、也不是报错")
                .isEmpty();
        // 放宽是显式授权的结果：同一个员工拿到 *:scope:all:query 后三个仓都出来
        assertThat(as(onlyA, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM), this::selectorCodes))
                .contains(warehouseA, warehouseB, seedWarehouseId());
    }

    @Test
    @DisplayName("停用仓即使有授权也不进选择器：ENABLED 过滤与范围过滤是两条 AND")
    void disabledWarehouseStaysOutOfSelector() {
        Long warehouse = newWarehouse("SD");
        Long employee = scopedEmployee(warehouse);
        disableWarehouse(warehouse);

        assertThat(as(employee, Set.of(), this::selectorCodes)).isEmpty();
        // 停用只把它移出「可下单的仓」，没有移出授权：管理页仍能读到它，否则没法把它再启用
        assertThat(as(employee, Set.of(), () -> queryIds(null))).contains(warehouse);
    }

    @Test
    @DisplayName("管理页分页同样收窄；表单里的编码筛选只能缩小、不能绕过范围")
    void queryPageIsNarrowedAndUserFilterOnlyShrinks() {
        Long warehouseA = newWarehouse("QA");
        Long warehouseB = newWarehouse("QB");
        Long onlyA = scopedEmployee(warehouseA);

        assertThat(as(onlyA, Set.of(), () -> queryIds(null))).contains(warehouseA);
        // 指名检索那个没授权的仓：结果为空，而不是「范围外的那条也带出来」
        assertThat(as(onlyA, Set.of(), () -> queryIds(warehouseCode(warehouseB)))).isEmpty();
        assertThat(as(onlyA, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> queryIds(warehouseCode(warehouseB)))).containsExactly(warehouseB);
    }

    @Test
    @DisplayName("详情：未授权按无权限回答（不是不存在），不存在仍是 40485，两种答案不得互换")
    void detailDistinguishesUnauthorizedFromMissing() {
        Long warehouseA = newWarehouse("DA");
        Long warehouseB = newWarehouse("DB");
        Long onlyA = scopedEmployee(warehouseA);

        assertThat(as(onlyA, Set.of(), () -> warehouseQueryService.detail(warehouseA).getId())).isEqualTo(warehouseA);
        assertNoPermission(() -> as(onlyA, Set.of(), () -> warehouseQueryService.detail(warehouseB)));
        expectCode(() -> as(onlyA, Set.of(), () -> warehouseQueryService.detail(-1L)), 40485);
        assertThat(as(onlyA, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> warehouseQueryService.detail(warehouseB).getId())).isEqualTo(warehouseB);
    }

    @Test
    @DisplayName("无登录上下文：选择器与管理页都返回空，不回退成全量")
    void missingIdentityFailsClosed() {
        newWarehouse("FC");
        SmartRequestUtil.remove();
        assertThat(warehouseQueryService.list()).isEmpty();

        WarehouseQueryForm form = new WarehouseQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        PageResult<WarehouseVO> page = warehouseQueryService.query(form);
        assertThat(page.getList()).isEmpty();
        assertThat(page.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必另写分支").isTrue();
    }

    @Test
    @DisplayName("失去仓库授权不影响历史单据：采购单读的是行上的仓库名称快照")
    void historicalOrderStillShowsWarehouseSnapshot() {
        Long warehouse = newWarehouse("HS");
        Long employee = newEmployee("HS1");
        Long skuId = newOnShelfSku("HS");
        Long supplierId = newPurchasableSupplier("HS", skuId);
        // 建单时他是这个仓的授权人（归属由服务端强制为调用者本人）
        grantWarehouseScope(employee, warehouse);
        Long orderId = as(employee, Set.of(), () -> {
            PurchaseOrderAddForm form = orderForm(supplierId, warehouse, skuId, "4.0000", "6.2000");
            return purchaseOrderService.create(form, prefix + ":hs:po").getId();
        });
        String snapshotName = warehouseName(warehouse);
        assertThat(as(employee, Set.of(), () -> purchaseQueryService.orderDetail(orderId).getWarehouseName()))
                .isEqualTo(snapshotName);

        // 收回授权：选择器立刻空掉，但那张历史单上的仓库名仍然读得到 —— 历史事实不是查询授权
        jdbc.update("DELETE FROM employee_warehouse_scope WHERE employee_id = ?", employee);
        evictMybatisCache();
        assertThat(as(employee, Set.of(), this::selectorCodes)).isEmpty();
        PurchaseOrderVO order = as(employee, Set.of(), () -> purchaseQueryService.orderDetail(orderId));
        assertThat(order.getWarehouseName()).isEqualTo(snapshotName);
        assertThat(order.getWarehouseId()).isEqualTo(warehouse);
    }

    // ==================== 夹具 ====================

    private Long newEmployee(String suffix) {
        String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                "仓库范围" + suffix);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("超管通过不构成权限证据（裁决第 5 条）").isFalse();
        return employeeId;
    }

    private Long scopedEmployee(Long warehouseId) {
        Long employeeId = newEmployee("W" + warehouseId);
        grantWarehouseScope(employeeId, warehouseId);
        return employeeId;
    }

    private void grantWarehouseScope(Long employeeId, Long warehouseId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouseId);
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("仓库可见性 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    /** 以某个员工 + 一组功能权限执行一段逻辑；不可嵌套（同一线程只能注册一次静态 mock）。 */
    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }

    private List<Long> selectorCodes() {
        return warehouseQueryService.list().stream().map(WarehouseVO::getId).toList();
    }

    /** {@code warehouseCode} 是页面上的一个普通筛选值：它只允许缩小授权范围，不允许绕过。 */
    private List<Long> queryIds(String warehouseCode) {
        WarehouseQueryForm form = new WarehouseQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        form.setWarehouseCode(warehouseCode);
        return warehouseQueryService.query(form).getList().stream().map(WarehouseVO::getId).toList();
    }

    private String warehouseCode(Long warehouseId) {
        return jdbc.queryForObject("SELECT warehouse_code FROM warehouse WHERE id = ?",
                String.class, warehouseId);
    }

    private String warehouseName(Long warehouseId) {
        return jdbc.queryForObject("SELECT name FROM warehouse WHERE id = ?", String.class, warehouseId);
    }

    private void assertNoPermission(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .isNotInstanceOf(ScmBusinessException.class)
                .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }
}
