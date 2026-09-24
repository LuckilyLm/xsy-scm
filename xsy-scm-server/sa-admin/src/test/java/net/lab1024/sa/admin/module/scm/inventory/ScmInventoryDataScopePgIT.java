package net.lab1024.sa.admin.module.scm.inventory;

import cn.dev33.satoken.stp.StpUtil;
import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryBalanceQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryMovementQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryBalanceVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryMovementVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferService;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseScopeUpdateForm;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseScopeService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * 库存族的仓库数据范围（{@code employee_warehouse_scope} → Mapper 谓词）取证。
 *
 * <p>口径来自 {@code docs/decisions.md}「P0 基线收口裁决」第 2、4、8 条，本类逐条钉住：
 * <ul>
 *   <li>仓库维度只认授权行，没有授权行就是<b>零行</b>，不存在「参数为空 = 全部」；</li>
 *   <li>无授权时得到的是<b>形状正常的空分页</b>（短路），而不是异常或全量；</li>
 *   <li>{@code scm:inventory:scope:all:query} 是「看全部」的<b>显式</b>授权；
 *       调用方自己传的 {@code warehouseId} 只能收窄，永远不能放大；</li>
 *   <li>调拨按「任一端授权即可见」，两端都不授权时详情按无权限回答；</li>
 *   <li>成本列无权时抹成 {@code null} 而不是 {@code 0}；</li>
 *   <li>授权行整体替换、活动组合由部分唯一索引挡住重复，回收后立即可见性消失
 *       —— 范围是<b>每次请求</b>解析的，不缓存在登录快照里。</li>
 * </ul>
 *
 * <p><b>身份与权限怎么造</b>：范围解析读两处外部状态 —— 登录员工（{@code SmartRequestUtil}）
 * 与功能权限（{@code StpUtil.hasPermission}）。IT 线程没有 Sa-Token 上下文，未桩的
 * {@code StpUtil} 调用会被 {@code ScmDataScopeService#hasPermission} 吞成 false，
 * 因此功能点一律由 {@code mockStatic} 显式给出；未点名的权限码取 Mockito 默认值 false。
 * 测试员工一律 {@code administratorFlag=false}（裁决第 5 条：超管通过不构成权限证据），
 * 只有「数据确实在、只是不在对方范围内」的反证才用超管上下文。{@link #as} 不可嵌套。
 *
 * <p><b>夹具</b>：两个仓库各自经真实的「采购单 → 收货确认」路径入库，因此每个仓都同时留下
 * 一行 {@code inventory_balance} 与一条 {@code PURCHASE_IN} 流水。仓库用新建的独立仓，
 * 余额与流水的期望条数才是可精确断言的数字；流水一律由命令服务写入
 * —— 它是 append-only 账本（Q7 / V21），测试不 UPDATE、不 DELETE。
 */
@DisplayName("库存仓库数据范围（PG IT）")
class ScmInventoryDataScopePgIT extends ScmW6PgITBase {

    /** 「查看全部仓库」与「查看成本字段」两个独立权限点，须与 V55 / V50 的 api_perms 逐字一致。 */
    private static final String WAREHOUSE_ALL = ScmDataScopeService.WAREHOUSE_ALL_PERM;
    private static final String COST = ScmReportAccess.COST_QUERY_PERM;

    /** 入库数量与采购单价：单价即 {@code inventory_movement.unit_cost} 与余额均价（无四舍五入分支）。 */
    private static final String QUANTITY = "10.0000";
    private static final String PRICE = "6.2000";
    private static final BigDecimal EXPECTED_AVG_COST = new BigDecimal("6.2000");
    private static final BigDecimal EXPECTED_AMOUNT = new BigDecimal("62.00");

    @Autowired
    private InventoryTransferService transferService;

    @Autowired
    private InventoryTransferQueryService transferQuery;

    @Autowired
    private WarehouseScopeService scopeMaintenance;

    // ------------------------------------------------------------------
    // 1. 授权一仓 ⇒ 只见该仓的余额与流水
    // ------------------------------------------------------------------

    @Test
    @DisplayName("只授权一个仓库：余额与流水都只剩该仓的行，分页 total 与实际行数一致")
    void authorizedEmployeeSeesOnlyItsOwnWarehouseRows() {
        Stocked pair = stockTwoWarehouses("ds1");
        Long keeperA = newEmployee("1A");
        Long keeperB = newEmployee("1B");
        authorize(keeperA, pair.warehouseA());
        authorize(keeperB, pair.warehouseB());

        var balancePage = as(keeperA, Set.of(), () -> inventoryBalanceQueryService.query(balanceQuery(null)));
        assertThat(balancePage.getTotal())
                .as("授权仓是全新仓，只装了本次造的那一行余额").isEqualTo(1L);
        assertThat(balancePage.getTotal()).isEqualTo(balancePage.getList().size());
        assertThat(balancePage.getList()).singleElement().satisfies(row -> {
            assertThat(row.getWarehouseId()).isEqualTo(pair.warehouseA());
            assertThat(row.getSkuId()).isEqualTo(pair.skuA());
        });

        var movementPage = as(keeperA, Set.of(), () -> inventoryMovementQueryService.query(movementQuery(null)));
        assertThat(movementPage.getTotal()).isEqualTo(1L);
        assertThat(movementPage.getList()).singleElement().satisfies(row -> {
            assertThat(row.getWarehouseId()).as("流水按所属仓库判定，与 created_by 无关").isEqualTo(pair.warehouseA());
            assertThat(row.getMovementType()).isEqualTo("PURCHASE_IN");
        });

        // 换成另一仓的授权人：同一份数据、同一句查询，看到的必须是另一行
        assertThat(warehouseIds(as(keeperB, Set.of(),
                () -> inventoryBalanceQueryService.query(balanceQuery(null))).getList()))
                .containsExactly(pair.warehouseB());
        assertThat(skuIds(as(keeperB, Set.of(),
                () -> inventoryMovementQueryService.query(movementQuery(null))).getList()))
                .containsExactly(pair.skuB());
    }

    // ------------------------------------------------------------------
    // 2. 无授权行 ⇒ 形状正常的空分页，不是异常，也不是全量
    // ------------------------------------------------------------------

    @Test
    @DisplayName("没有任何仓库授权：得到 total=0 的空分页而不是异常；数据仍在（超管反证）")
    void employeeWithoutAnyAuthorizationGetsWellFormedEmptyPage() {
        Stocked pair = stockTwoWarehouses("ds2");
        Long unscoped = newEmployee("2A");

        var balances = as(unscoped, Set.of(), () -> inventoryBalanceQueryService.query(balanceQuery(null)));
        assertThat(balances.getList()).isEmpty();
        assertThat(balances.getTotal()).isZero();
        assertThat(balances.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必为「无授权」另写分支").isTrue();
        assertThat(balances.getPageNum()).isEqualTo(1L);
        assertThat(balances.getPageSize()).as("分页参数按请求原样回显").isEqualTo(50L);

        var movements = as(unscoped, Set.of(), () -> inventoryMovementQueryService.query(movementQuery(null)));
        assertThat(movements.getTotal()).isZero();
        assertThat(movements.getEmptyFlag()).isTrue();
        assertThat(movements.getList()).isEmpty();

        // 反证：这两行确实躺在库里 —— 上面得到的 0 是「过滤」的结果，不是「没有夹具」
        var adminView = asAdmin(() -> inventoryBalanceQueryService.query(balanceQuery(null)));
        assertThat(adminView.getTotal()).isEqualTo(2L);
        assertThat(warehouseIds(adminView.getList()))
                .containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());
        assertThat(asAdmin(() -> inventoryMovementQueryService.query(movementQuery(null))).getTotal())
                .as("两个仓各有一条入库流水").isEqualTo(2L);
    }

    // ------------------------------------------------------------------
    // 3. 全量范围权限 + 调用方筛选：只能收窄，不能放大
    // ------------------------------------------------------------------

    @Test
    @DisplayName("持全部仓库权限者两仓皆可见；仓库筛选放大不了未授权的范围")
    void allScopePermissionSeesEverythingAndFilterOnlyNarrows() {
        Stocked pair = stockTwoWarehouses("ds3");
        // 一条授权行都没有：只有显式的 scope:all 权限才能放宽到全量
        Long headkeeper = newEmployee("3A");

        assertThat(warehouseIds(as(headkeeper, Set.of(WAREHOUSE_ALL),
                () -> inventoryBalanceQueryService.query(balanceQuery(null)).getList())))
                .containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());

        // 全量范围内按仓库筛选：结果落在筛选值上，total 同步收窄
        var narrowed = as(headkeeper, Set.of(WAREHOUSE_ALL),
                () -> inventoryBalanceQueryService.query(balanceQuery(pair.warehouseA())));
        assertThat(narrowed.getTotal()).isEqualTo(1L);
        assertThat(warehouseIds(narrowed.getList())).containsExactly(pair.warehouseA());

        // 同一个筛选交给只授权 A 仓的人去问 B 仓：必须是空，而不是「筛选条件最大」
        Long keeperA = newEmployee("3B");
        authorize(keeperA, pair.warehouseA());
        var widened = as(keeperA, Set.of(),
                () -> inventoryBalanceQueryService.query(balanceQuery(pair.warehouseB())));
        assertThat(widened.getList()).as("范围谓词与筛选条件是 AND 关系，筛选永远放大不了范围").isEmpty();
        assertThat(widened.getTotal()).isZero();
        assertThat(warehouseIds(as(keeperA, Set.of(),
                () -> inventoryBalanceQueryService.query(balanceQuery(null)).getList())))
                .containsExactly(pair.warehouseA());

        // 空清单的授权不等于「全部」：同一个员工拿掉权限点后仍是零行
        assertThat(as(keeperA, Set.of(WAREHOUSE_ALL),
                () -> inventoryBalanceQueryService.query(balanceQuery(null)).getTotal())).isEqualTo(2L);
    }

    // ------------------------------------------------------------------
    // 4. 调拨：任一端授权即可见
    // ------------------------------------------------------------------

    @Test
    @DisplayName("调拨在途期间源仓与目标仓的授权人都看得到同一张单，第三方看不到")
    void transferIsVisibleFromEitherEndWarehouse() {
        loginAsAdmin();
        Long sku = newSkuOfType("ds4", "NON_STANDARD", "ON_SHELF");
        Long from = newWarehouse("F");
        Long to = newWarehouse("T");
        Long elsewhere = newWarehouse("X");
        stockIn(from, sku, "ds4");

        var draft = new InventoryTransferAddForm();
        draft.setFromWarehouseId(from);
        draft.setToWarehouseId(to);
        InventoryTransferAddForm.Item line = new InventoryTransferAddForm.Item();
        line.setSkuId(sku);
        line.setQuantity(new BigDecimal("4.0000"));
        draft.setItems(new ArrayList<>(List.of(line)));
        Long transferId = transferService.create(draft);
        String transferNo = jdbc.queryForObject(
                "SELECT transfer_no FROM inventory_transfer WHERE id = ?", String.class, transferId);
        // 在途：货已离开源仓、尚未进入目标仓，此期间单据对双方都必须可见
        transferService.ship(transferId);

        Long sourceKeeper = newEmployee("4A");
        Long targetKeeper = newEmployee("4B");
        Long stranger = newEmployee("4C");
        authorize(sourceKeeper, from);
        authorize(targetKeeper, to);
        authorize(stranger, elsewhere);

        assertThat(visibleTransferNos(sourceKeeper, transferNo))
                .as("只授权源仓的人：发出去的货在路上仍归他管").containsExactly(transferNo);
        assertThat(visibleTransferNos(targetKeeper, transferNo))
                .as("只授权目标仓的人：正送往自己仓的货必须可见").containsExactly(transferNo);
        assertThat(visibleTransferNos(stranger, transferNo)).isEmpty();

        // 在途报表与调拨列表是同一谓词形态的另一处（mapper 里各有一份 <if>）
        assertThat(inTransitTransferNos(sourceKeeper)).contains(transferNo);
        assertThat(inTransitTransferNos(targetKeeper)).contains(transferNo);
        assertThat(inTransitTransferNos(stranger)).doesNotContain(transferNo);

        assertThat(as(sourceKeeper, Set.of(), () -> transferQuery.detail(transferId).getId())).isEqualTo(transferId);
        assertThat(as(targetKeeper, Set.of(), () -> transferQuery.detail(transferId).getId())).isEqualTo(transferId);
        // 两端都不授权：按无权限回答，与「不存在」区分开
        assertDataScopeDenied(() -> as(stranger, Set.of(), () -> transferQuery.detail(transferId)));
    }

    // ------------------------------------------------------------------
    // 5. 详情按 id 越权：独立异常类型 + 30005 信封
    // ------------------------------------------------------------------

    @Test
    @DisplayName("读未授权仓库的余额详情：抛 ScmDataScopeException，由 ScmExceptionHandler 出 30005")
    void detailOutsideAuthorizedWarehouseThrowsDataScopeException() {
        Stocked pair = stockTwoWarehouses("ds5");
        Long keeperA = newEmployee("5A");
        authorize(keeperA, pair.warehouseA());
        Long ownBalanceId = balanceRow(pair.warehouseA(), pair.skuA()).getId();
        Long foreignBalanceId = balanceRow(pair.warehouseB(), pair.skuB()).getId();

        assertThat(as(keeperA, Set.of(), () -> inventoryBalanceQueryService.detail(ownBalanceId).getWarehouseId()))
                .isEqualTo(pair.warehouseA());
        assertDataScopeDenied(() -> as(keeperA, Set.of(), () -> inventoryBalanceQueryService.detail(foreignBalanceId)));

        // 越权判定不能把「真的不存在」也吞成 30005，否则探测 id 与探测权限混成一种结果
        expectCode(() -> as(keeperA, Set.of(),
                () -> inventoryBalanceQueryService.detail(foreignBalanceId + 9999999L)), 40486);
    }

    // ------------------------------------------------------------------
    // 6. 成本列：无权时是 null，不是 0
    // ------------------------------------------------------------------

    @Test
    @DisplayName("无成本权限时 avg_cost / 账面金额 / unit_cost 一律为 null；有权限才给出非零数字")
    void costColumnsAreMaskedToNullRatherThanZero() {
        Stocked pair = stockTwoWarehouses("ds6");
        Long keeper = newEmployee("6A");
        authorize(keeper, pair.warehouseA());
        // 库里的真实成本非零：下面得到的 null 只能是「抹掉」的结果，不是数据缺失
        assertThat(balanceRow(pair.warehouseA(), pair.skuA()).getAvgCost())
                .as("前置：均价事实确实存在").isEqualByComparingTo(EXPECTED_AVG_COST);

        // 每次读之前清一级缓存：无权限的那次读会把返回的 VO 实例就地抹成 null，
        // 而同一事务里同一语句同一参数的第二次读会拿回同一批实例（生产上每请求一个
        // SqlSession，不会串味；测试在一个事务里换权限，就必须自己隔开）。
        var hiddenBalance = costVisibleRows(keeper, false);
        assertThat(hiddenBalance.getAvgCost()).as("无权时必须是 null：0 会被读成「这批货没有成本」").isNull();
        assertThat(hiddenBalance.getAmount()).isNull();
        assertThat(hiddenBalance.getQuantity())
                .as("数量不是成本事实，权限收口不能顺手把它藏掉").isEqualByComparingTo(QUANTITY);
        assertThat(hiddenBalance.getWarehouseId()).isEqualTo(pair.warehouseA());

        var hiddenMovement = costVisibleMovement(keeper, false);
        assertThat(hiddenMovement.getUnitCost()).isNull();
        assertThat(hiddenMovement.getQuantity()).isEqualByComparingTo(QUANTITY);

        // 详情是同一条事实的另一个入口：只挡列表等于把成本留给详情页
        var hiddenDetail = balanceDetail(keeper, hiddenBalance.getId(), false);
        assertThat(hiddenDetail.getAvgCost()).isNull();
        assertThat(hiddenDetail.getAmount()).isNull();

        var shownBalance = costVisibleRows(keeper, true);
        assertThat(shownBalance.getAvgCost()).isNotNull().isNotZero().isEqualByComparingTo(EXPECTED_AVG_COST);
        assertThat(shownBalance.getAmount()).isNotNull().isNotZero().isEqualByComparingTo(EXPECTED_AMOUNT);
        assertThat(costVisibleMovement(keeper, true).getUnitCost())
                .isNotNull().isNotZero().isEqualByComparingTo(PRICE);
        assertThat(balanceDetail(keeper, shownBalance.getId(), true).getAvgCost())
                .isNotNull().isNotZero().isEqualByComparingTo(EXPECTED_AVG_COST);
    }

    /** 余额列表首行；{@code costVisible} 只改功能权限，不改数据范围，两次读的唯一差别就是这一位。 */
    private InventoryBalanceVO costVisibleRows(Long employeeId, boolean costVisible) {
        evictMybatisCache();
        Set<String> permissions = costVisible ? Set.of(COST) : Set.of();
        return as(employeeId, permissions,
                () -> inventoryBalanceQueryService.query(balanceQuery(null)).getList()).getFirst();
    }

    private InventoryMovementVO costVisibleMovement(Long employeeId, boolean costVisible) {
        evictMybatisCache();
        Set<String> permissions = costVisible ? Set.of(COST) : Set.of();
        return as(employeeId, permissions,
                () -> inventoryMovementQueryService.query(movementQuery(null)).getList()).getFirst();
    }

    private InventoryBalanceVO balanceDetail(Long employeeId, Long balanceId, boolean costVisible) {
        evictMybatisCache();
        Set<String> permissions = costVisible ? Set.of(COST) : Set.of();
        return as(employeeId, permissions, () -> inventoryBalanceQueryService.detail(balanceId));
    }

    // ------------------------------------------------------------------
    // 7. 授权维护：整体替换、回收即失效，且逐请求生效
    // ------------------------------------------------------------------

    @Test
    @DisplayName("授权整体替换与回收：同一登录上下文里可见范围立即跟着授权行走")
    void replacementAndRecycleChangeVisibilityWithoutRelogin() {
        Stocked pair = stockTwoWarehouses("ds7");
        Long keeper = newEmployee("7A");

        scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA())));
        assertThat(authorizedWarehouseIds(keeper)).containsExactly(pair.warehouseA());
        assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseA());

        scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA(), pair.warehouseB())));
        assertThat(authorizedWarehouseIds(keeper)).containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());
        assertThat(visibleWarehouseIds(keeper))
                .containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());

        // 替换不是追加：收掉 B 之后活动行只剩 A，B 仓的余额行随即消失
        scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA())));
        assertThat(authorizedWarehouseIds(keeper)).containsExactly(pair.warehouseA());
        assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseA());
        assertThat(activeScopeRowCount(keeper)).isEqualTo(1);

        // 全部回收：数据没动，可见性必须归零（证明范围逐请求解析，不缓存在登录快照里）
        scopeMaintenance.update(scopeForm(keeper, List.of()));
        assertThat(activeScopeRowCount(keeper)).isZero();
        var emptied = balancesOf(keeper);
        assertThat(emptied.getTotal()).isZero();
        assertThat(emptied.getEmptyFlag()).isTrue();
        assertThat(asAdmin(() -> inventoryBalanceQueryService.query(balanceQuery(null)).getTotal())).isEqualTo(2L);

        // 回收过的组合可以再授权：部分唯一索引只覆盖活动行
        assertThatCode(() -> scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseB()))))
                .doesNotThrowAnyException();
        assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseB());
    }

    @Test
    @DisplayName("活动授权唯一由部分唯一索引兜住；维护失败不得清空原有范围")
    void duplicateActivePairRejectedByIndexAndFailedUpdateKeepsScope() {
        Stocked pair = stockTwoWarehouses("ds8");
        Long keeper = newEmployee("8A");
        scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA())));

        // 绕过服务直接写同一组合的第二条活动授权：必须在库内失败，而不是靠 Java 去重
        expectSqlFailure("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                keeper, pair.warehouseA());
        // 回收之后再写同一条是允许的（历史行不占唯一位）
        jdbc.update("UPDATE employee_warehouse_scope SET deleted_flag = TRUE WHERE employee_id = ? AND warehouse_id = ?",
                keeper, pair.warehouseA());
        evictMybatisCache();
        assertThatCode(() -> jdbc.update(
                "INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                keeper, pair.warehouseA())).doesNotThrowAnyException();

        // 清单里重复的 id 由服务先去重：写成两行会撞上面的唯一索引，而语义上只是同一个仓
        scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA(), pair.warehouseA())));
        assertThat(activeScopeRowCount(keeper)).isEqualTo(1);

        // 指向不存在的仓库：不建外键，完整性只能在写入侧判掉；原有授权不能被这次失败清掉
        expectCode(() -> scopeMaintenance.update(scopeForm(keeper, List.of(999999999L))), 40485);
        assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseA());

        // 已删除员工不接受授权：垃圾行既命中不到数据也删不掉
        Long retired = newEmployee("8B");
        jdbc.update("UPDATE t_employee SET deleted_flag = TRUE WHERE employee_id = ?", retired);
        expectCode(() -> scopeMaintenance.update(scopeForm(retired, List.of(pair.warehouseA()))), 40000);
    }

    // ==================================================================
    // 夹具
    // ==================================================================

    /** 两个独立新仓库，各有一行余额和一条 {@code PURCHASE_IN} 流水。 */
    private record Stocked(Long warehouseA, Long skuA, Long warehouseB, Long skuB) {
    }

    private Stocked stockTwoWarehouses(String tag) {
        loginAsAdmin();
        Long warehouseA = newWarehouse(tag + "A");
        Long warehouseB = newWarehouse(tag + "B");
        Long skuA = newSkuOfType(tag + "A", "NON_STANDARD", "ON_SHELF");
        Long skuB = newSkuOfType(tag + "B", "NON_STANDARD", "ON_SHELF");
        stockIn(warehouseA, skuA, tag + "SA");
        stockIn(warehouseB, skuB, tag + "SB");
        return new Stocked(warehouseA, skuA, warehouseB, skuB);
    }

    /**
     * 真实「采购单 → 提交 → 收货 → 确认」入库到指定仓库：
     * 余额与流水必须由命令服务写，测试不往账本里直插行。
     */
    private void stockIn(Long warehouseId, Long skuId, String tag) {
        Long supplierId = newPurchasableSupplier(tag, skuId);
        var order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, QUANTITY, PRICE), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        var receipt = createReceipt(order.getId());
        confirmReceipt(receipt.getId(), QUANTITY);
    }

    /** 直接插授权行：本类要测的是读侧按授权行收窄，写侧另有第 7 组用例。 */
    private void authorize(Long employeeId, Long warehouseId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouseId);
        evictMybatisCache();
    }

    /** 一名 {@code administratorFlag=false} 的真实员工：范围判定只看 employee_id 与 deleted_flag。 */
    private Long newEmployee(String tag) {
        loginAsAdmin();
        String loginName = (prefix + "-" + tag).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "仓管" + tag);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("正式业务角色的验收账号禁止超管位（裁决第 5 条）").isFalse();
        evictMybatisCache();
        return employeeId;
    }

    private WarehouseScopeUpdateForm scopeForm(Long employeeId, List<Long> warehouseIds) {
        loginAsAdmin();
        WarehouseScopeUpdateForm form = new WarehouseScopeUpdateForm();
        form.setEmployeeId(employeeId);
        // 表单约定：清空请传空数组，而不是 null
        form.setWarehouseIds(new ArrayList<>(warehouseIds));
        return form;
    }

    // ------------------------------------------------------------------
    // 查询请求与结果投影
    // ------------------------------------------------------------------

    private InventoryBalanceQueryForm balanceQuery(Long warehouseId) {
        InventoryBalanceQueryForm form = page(new InventoryBalanceQueryForm());
        // SKU 编码前缀是本用例独有的：把断言限定在自己造的那几行上，不假设库是空的
        form.setSkuCode(prefix);
        form.setWarehouseId(warehouseId);
        return form;
    }

    private InventoryMovementQueryForm movementQuery(Long warehouseId) {
        InventoryMovementQueryForm form = page(new InventoryMovementQueryForm());
        form.setSkuCode(prefix);
        form.setWarehouseId(warehouseId);
        return form;
    }

    private <T extends PageParam> T page(T form) {
        form.setPageNum(1L);
        form.setPageSize(50L);
        return form;
    }

    private PageResult<InventoryBalanceVO> balancesOf(Long employeeId) {
        return as(employeeId, Set.of(), () -> inventoryBalanceQueryService.query(balanceQuery(null)));
    }

    /** 该员工此刻能看到的余额行落在哪些仓库上 —— 授权一变，这个集合就该跟着变。 */
    private List<Long> visibleWarehouseIds(Long employeeId) {
        return warehouseIds(balancesOf(employeeId).getList());
    }

    private List<String> visibleTransferNos(Long employeeId, String transferNo) {
        return as(employeeId, Set.of(), () -> {
            InventoryTransferQueryForm form = page(new InventoryTransferQueryForm());
            // 按单号定位：仓库两端都不授权时，这一行必须整体消失，而不是「少几列」
            form.setTransferNo(transferNo);
            return transferQuery.queryPage(form).getList().stream()
                    .map(row -> row.getTransferNo()).toList();
        });
    }

    private List<String> inTransitTransferNos(Long employeeId) {
        return as(employeeId, Set.of(), () -> transferQuery.queryInTransit().stream()
                .map(row -> row.getTransferNo()).toList());
    }

    private static List<Long> warehouseIds(List<InventoryBalanceVO> rows) {
        return rows.stream().map(InventoryBalanceVO::getWarehouseId).toList();
    }

    private static List<Long> skuIds(List<InventoryMovementVO> rows) {
        return rows.stream().map(InventoryMovementVO::getSkuId).toList();
    }

    private List<Long> authorizedWarehouseIds(Long employeeId) {
        var rows = scopeMaintenance.listWarehouses(employeeId);
        return rows.stream().map(row -> row.getWarehouseId()).toList();
    }

    private int activeScopeRowCount(Long employeeId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT count(*) FROM employee_warehouse_scope"
                        + " WHERE employee_id = ? AND deleted_flag = FALSE", Integer.class, employeeId);
    }

    // ------------------------------------------------------------------
    // 身份与权限
    // ------------------------------------------------------------------

    /** 夹具与授权维护用的「不受数据范围约束的操作者」，与基类 {@code setUpOperator} 同一身份。 */
    private void loginAsAdmin() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Scope IT admin");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("库存数据范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    /**
     * 以某个非超管员工 + 一组功能权限执行一段逻辑。未点名的权限码取 Mockito 默认值 false，
     * 与 {@code ScmDataScopeService} 的失败关闭取向一致。<b>不可嵌套调用。</b>
     */
    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }

    /** 超管位只用于「数据确实在」的反证，不作为任何权限结论的依据（裁决第 5 条）。 */
    private <T> T asAdmin(Supplier<T> body) {
        loginAsAdmin();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            return body.get();
        }
    }

    /**
     * 越权读必须是独立的 {@link ScmDataScopeException}：既不能是带出自己业务码的
     * {@code ScmBusinessException}，也不能伪装成「不存在」。信封出码由 {@link ScmExceptionHandler}
     * 负责（30005），本类直接把映射结果也算一遍，避免只断到异常类型而放走映射缺失。
     */
    private void assertDataScopeDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .isExactlyInstanceOf(ScmDataScopeException.class)
                .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
        var envelope = new ScmExceptionHandler().handleDataScope(new ScmDataScopeException());
        assertThat(envelope.getCode()).isEqualTo(UserErrorCode.NO_PERMISSION.getCode());
        assertThat(envelope.getMsg()).isEqualTo(UserErrorCode.NO_PERMISSION.getMsg());
        assertThat(envelope.getOk()).isFalse();
    }
}
