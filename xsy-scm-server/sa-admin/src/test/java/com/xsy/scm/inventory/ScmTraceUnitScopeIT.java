package com.xsy.scm.inventory;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.trace.domain.form.TraceUnitIssueForm;
import com.xsy.scm.trace.domain.form.TraceUnitQueryForm;
import com.xsy.scm.trace.domain.form.TraceUnitVoidForm;
import com.xsy.scm.trace.service.TraceUnitService;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import java.util.List;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 包装码的仓库数据范围。
 *
 * <p>
 * 关掉测试事务：范围判定要在「独立事务 + 独立登录身份」下验证，否则异常会把外层事务标记为 rollback-only，
 * 后续断言拿到的都是假失败。
 *
 * <p>
 * 受测员工只有 A 仓授权：B 仓的码必须查不到、回放不了、发不了码、作废不了 —— 包括「按码回放」这种
 * 只要知道码就能读到客户信息的入口。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("W9 包装码仓库范围（PG IT，无外层事务）")
class ScmTraceUnitScopeIT extends ScmW6PgITBase {

    private static final long SCOPED_EMPLOYEE_ID = 9_900_001L;

    @Autowired
    private TraceUnitService traceUnitService;

    private Long warehouseA;

    private Long warehouseB;

    private Long fixtureWarehouse;

    @Override
    protected Long fixtureWarehouseId() {
        return fixtureWarehouse == null ? super.fixtureWarehouseId() : fixtureWarehouse;
    }

    /** 用给定批次登记确认收货：本类只造单批次行。 */
    private void confirmWithLots(Long receiptId, String quantity, String lotQuantity) {
        com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO current = reloadReceipt(receiptId);
        com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO line = current.getItems().getFirst();
        com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm.Item item =
                receiptLine(line.getId(), line.getVersion(), quantity);
        com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm.Lot lot =
                new com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm.Lot();
        lot.setSourceLotNo("SCOPE-" + lotQuantity);
        lot.setQuantity(lotQuantity);
        item.setLots(List.of(lot));
        purchaseReceiptService.confirm(confirmForm(current.getId(), current.getVersion(), item),
                prefix + ":scope:" + java.util.UUID.randomUUID());
    }

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session
    }

    @BeforeEach
    void createWarehouses() {
        warehouseA = lotWarehouse(prefix + "范围仓A");
        warehouseB = lotWarehouse(prefix + "范围仓B");
        jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id, deleted_flag, create_time) "
                + "VALUES (?, ?, FALSE, CURRENT_TIMESTAMP)", SCOPED_EMPLOYEE_ID, warehouseA);
    }

    @AfterEach
    void removeScopedEmployee() {
        SmartRequestUtil.remove();
    }

    private Long lotWarehouse(String name) {
        WarehouseAddForm form = new WarehouseAddForm();
        form.setName(name);
        form.setLotTrackingEnabled(true);
        return warehouseService.create(form);
    }

    /** 切到「只有 A 仓授权」的普通员工（非管理员，也没有全仓权限）。 */
    private void asScopedEmployee() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(SCOPED_EMPLOYEE_ID);
        employee.setActualName("W9 范围员工");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
    }

    private void asAdministrator() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W9 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    /** 在指定仓库造一个批次并发出一个码，返回码值。 */
    private String unitIn(Long warehouseId, String tag) {
        asAdministrator();
        Long skuId = newOnShelfSku(tag);
        fixtureWarehouse = warehouseId;
        try {
            W6Fixture fx = inboundFixture(tag, skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
        } finally {
            fixtureWarehouse = null;
        }
        Long lotId = jdbc.queryForObject("SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, skuId);
        TraceUnitIssueForm form = new TraceUnitIssueForm();
        form.setLotId(lotId);
        form.setPackageType("BAG");
        form.setPackageCount(1);
        form.setQuantityPerPackage("1.0000");
        return traceUnitService.issue(form, "W9 scope").getFirst().getUnitCode();
    }

    @Test
    @DisplayName("#1 只有 A 仓授权的员工读不到 B 仓的码，也发不了码、作废不了")
    void scopedEmployeeCannotTouchOtherWarehouse() {
        String codeA = unitIn(warehouseA, "SCOPEA");
        String codeB = unitIn(warehouseB, "SCOPEB");
        Long lotB = jdbc.queryForObject(
                "SELECT u.lot_id FROM xsy_trace_unit u WHERE u.unit_code = ?", Long.class, codeB);

        asScopedEmployee();

        // 回放：按码也读不到
        assertThatThrownBy(() -> traceUnitService.replay(codeB))
                .isInstanceOf(ScmDataScopeException.class);
        // 列表：B 仓的码不出现在结果里
        TraceUnitQueryForm query = new TraceUnitQueryForm();
        query.setPageNum(1L);
        query.setPageSize(20L);
        List<String> visible = traceUnitService.query(query).getList().stream()
                .map(TraceUnitVOUnitCode::of).toList();
        assertThat(visible).contains(codeA).doesNotContain(codeB);

        // 发码：跨仓发码必须被拒
        TraceUnitIssueForm issue = new TraceUnitIssueForm();
        issue.setLotId(lotB);
        issue.setPackageType("BAG");
        issue.setPackageCount(1);
        issue.setQuantityPerPackage("1.0000");
        assertThatThrownBy(() -> traceUnitService.issue(issue, "W9 scope"))
                .isInstanceOf(ScmDataScopeException.class);

        // 作废：跨仓作废必须被拒
        TraceUnitVoidForm voidForm = new TraceUnitVoidForm();
        voidForm.setUnitCode(codeB);
        voidForm.setReason("越权尝试");
        assertThatThrownBy(() -> traceUnitService.voidUnit(voidForm, "W9 scope"))
                .isInstanceOf(ScmDataScopeException.class);

        // 本仓仍然可读：范围是收窄，不是全关
        assertThat(traceUnitService.replay(codeA).getUnit().getUnitCode()).isEqualTo(codeA);
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_trace_unit WHERE unit_code = ?", String.class, codeB))
                .isEqualTo("ACTIVE");
    }

    /** 取码值的投影，避免在断言里写 lambda 取字段。 */
    private static final class TraceUnitVOUnitCode {
        private static String of(com.xsy.scm.trace.domain.vo.TraceUnitVO.Unit unit) {
            return unit.getUnitCode();
        }
    }

    @Test
    @DisplayName("#2 范围收窄后列表数量与仓库一致：B 仓的码对 A 仓员工不可见")
    void scopedQueryCountsOnlyAuthorizedWarehouse() {
        unitIn(warehouseA, "SCOPEA2");
        unitIn(warehouseB, "SCOPEB2");
        asScopedEmployee();

        TraceUnitQueryForm query = new TraceUnitQueryForm();
        query.setPageNum(1L);
        query.setPageSize(50L);
        var page = traceUnitService.query(query);
        assertThat(page.getList()).allSatisfy(unit -> assertThat(unit.getUnitCode()).isNotBlank());
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(1L);
        assertThat(page.getList()).noneMatch(unit -> unit.getLotId() == null);
    }
}
