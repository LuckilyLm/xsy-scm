package net.lab1024.sa.admin.module.scm.report;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import cn.dev33.satoken.stp.StpUtil;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.report.controller.ScmReportController;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmInventoryReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmOverviewReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmPurchaseReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmReceiptReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmSalesReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.InventoryReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.PurchaseReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReceiptReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportDailyStatVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportOverviewVO;
import net.lab1024.sa.admin.module.scm.report.service.InventoryReportService;
import net.lab1024.sa.admin.module.scm.report.service.OverviewReportService;
import net.lab1024.sa.admin.module.scm.report.service.PurchaseReportService;
import net.lab1024.sa.admin.module.scm.report.service.ReceiptReportService;
import net.lab1024.sa.admin.module.scm.report.service.SalesReportService;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mockStatic;

/**
 * P0-F 报表中心的仓库数据范围（真实 PostgreSQL IT）。
 *
 * <p>被测口径来自 {@code docs/decisions.md}「P0 基线收口裁决」第 2、4、8、10 条，其中第 10 条是
 * 本类存在的理由：<b>财务不等于代码里的全组织可见</b>，报表的页面查询与 Excel 导出必须用同一套
 * 数据范围 —— 能看 A/B 仓就只能导 A/B 仓，{@code scm:report:export} 只代表允许导出，绝不扩大查询范围。
 *
 * <p><b>为什么必须钉住聚合数字而不只是行集</b>：报表的谓词坐在 CTE 与派生表里面，放错位置改出来的
 * 是一个金额而不是一组行，只断言行集的测试发现不了。因此核心断言是
 * {@link #scopedAmountsAddUpToUnionScope()} 里那条恒等式：<b>两个互斥仓库范围的金额之和
 * 等于其并集范围的金额</b>。范围一旦落到维度 JOIN 上或聚合之后，恒等式立刻不成立。
 *
 * <p><b>夹具用两个本次新建的仓库</b>：新仓里除了本用例写入的事实不可能有别的行，范围金额才能按
 * 精确值断言，不必退化成「包含 / 不包含」。库存事实一律走真实命令链（采购单 → 收货确认）造，
 * 直插 {@code inventory_movement} 会绕过 append-only 账本的快照约束。
 *
 * <p><b>身份与权限怎么造</b>：与 {@code ScmPurchaseDataScopePgIT} 同一套做法 —— 登录员工用
 * {@link SmartRequestUtil#setRequestUser}，功能权限用 {@code mockStatic(StpUtil.class)}（IT 线程里
 * 没有 Sa-Token 上下文，未点名的权限码取 Mockito 默认值 false，正好等于失败关闭）。
 * 测试员工一律 {@code administratorFlag=false}（裁决第 5 条：超管通过不构成权限证据）；
 * 超管那一例是刻意保留的对照组，用来证明范围收口没有改动 break-glass 语义。
 * {@link #as} 不可嵌套：同一线程只能注册一次静态 mock。基类的 {@code @AfterEach} 负责清登录态。
 */
@DisplayName("P0-F 报表仓库数据范围与导出口径（PG IT）")
class ScmReportDataScopePgIT extends ScmW6PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 每个测试仓两单：DIRECT 收货入库 4.0000 × 6.2000，另有一单待入库 3.0000 × 5.0000。 */
    private static final String DIRECT_QUANTITY = "4.0000";

    private static final String DIRECT_PRICE = "6.2000";

    private static final String PENDING_QUANTITY = "3.0000";

    private static final String PENDING_PRICE = "5.0000";

    /** 单个新建仓库的采购承诺合计（24.8 + 15），两仓恒等式与逐仓断言都靠它。 */
    private static final BigDecimal WAREHOUSE_PURCHASE_AMOUNT = new BigDecimal("39.8000");

    private static final BigDecimal WAREHOUSE_INBOUND_COST = new BigDecimal("24.8000");

    /** xlsx 是 zip 容器，读回需要随机访问，因此导出字节先落到 JUnit 负责清理的临时目录。 */
    @TempDir
    private Path workbookDir;

    @Autowired
    private ScmReportController reportController;

    @Autowired
    private OverviewReportService overviewReportService;

    @Autowired
    private SalesReportService salesReportService;

    @Autowired
    private PurchaseReportService purchaseReportService;

    @Autowired
    private ReceiptReportService receiptReportService;

    @Autowired
    private InventoryReportService inventoryReportService;

    private Long warehouseA;
    private Long warehouseB;
    private String warehouseNameA;
    private String warehouseNameB;
    private WarehouseFacts factsA;
    private WarehouseFacts factsB;
    private Long scopedToA;
    private Long scopedToB;
    private Long scopedToBoth;
    private Long withoutAnyWarehouse;

    /** 一个仓库内的报表事实：两张已提交采购单，其中一张已入库、另一张待入库。 */
    private record WarehouseFacts(Long warehouseId, Long skuId, String skuCode, Long inboundOrderId,
                                 Long pendingOrderId, Long inboundReceiptId, Long pendingReceiptId) {
    }

    @BeforeEach
    void setUpWarehousesAndEmployees() {
        // 基类的登录上下文是 administratorFlag=true，因此下列造数不受任何范围约束。
        warehouseA = newWarehouse("RPA");
        warehouseB = newWarehouse("RPB");
        warehouseNameA = warehouseName(warehouseA);
        warehouseNameB = warehouseName(warehouseB);
        factsA = stockWarehouse(warehouseA, "A");
        factsB = stockWarehouse(warehouseB, "B");

        scopedToA = newEmployee("RPA-EMP");
        scopedToB = newEmployee("RPB-EMP");
        scopedToBoth = newEmployee("RPAB-EMP");
        withoutAnyWarehouse = newEmployee("RPNONE-EMP");
        grantWarehouseScope(scopedToA, warehouseA);
        grantWarehouseScope(scopedToB, warehouseB);
        grantWarehouseScope(scopedToBoth, warehouseA);
        grantWarehouseScope(scopedToBoth, warehouseB);
    }

    // ==================== 1. 逐页与逐表都按仓库收窄 ====================

    @Test
    @DisplayName("只授权甲仓：库存流水 / 入库 / 收货 / 待入库 / 价值 / 收发存 / 采购各维度只剩甲仓")
    void everyScopedQueryReturnsOnlyTheAuthorizedWarehouse() {
        Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);

        List<InventoryReportVO.MovementRow> movements = as(scopedToA, cost,
                () -> inventoryReportService.movementList(inventoryForm()).getList());
        assertThat(movements).as("甲仓只有 DIRECT 收货产生的那一条 PURCHASE_IN 流水")
                .extracting(InventoryReportVO.MovementRow::getSkuCode).containsExactly(factsA.skuCode());
        assertThat(movements).allSatisfy(row -> assertThat(row.getWarehouseId()).isEqualTo(warehouseA));

        assertThat(as(scopedToA, cost, () -> receiptReportService.inboundList(receiptForm()).getList()))
                .extracting(ReceiptReportVO.InboundRow::getSkuCode).containsExactly(factsA.skuCode());

        assertThat(as(scopedToA, cost, () -> receiptReportService.receiptList(receiptForm()).getList()))
                .as("两张已确认收货单都是甲仓的，乙仓的一张都不给")
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.getWarehouseName()).isEqualTo(warehouseNameA))
                .noneSatisfy(row -> assertThat(row.getWarehouseName()).isEqualTo(warehouseNameB));

        assertThat(as(scopedToA, cost, () -> receiptReportService.pendingPutawayList(receiptForm()).getList()))
                .extracting(ReceiptReportVO.PendingPutawayRow::getReceiptNo)
                .containsExactly(receiptNo(factsA.pendingReceiptId()));

        assertThat(as(scopedToA, cost, () -> inventoryReportService.valueList(inventoryForm()).getList()))
                .allSatisfy(row -> assertThat(row.getWarehouseId()).isEqualTo(warehouseA))
                .extracting(InventoryReportVO.ValueRow::getSkuCode).containsExactly(factsA.skuCode());

        List<InventoryReportVO.FlowSummaryRow> flows = as(scopedToA, cost,
                () -> inventoryReportService.flowSummary(inventoryForm()).getList());
        assertThat(flows).hasSize(1);
        assertThat(flows.getFirst().getWarehouseId()).isEqualTo(warehouseA);
        assertThat(flows.getFirst().getPurchaseInQuantity()).isEqualByComparingTo(DIRECT_QUANTITY);

        PurchaseReportVO.Overview purchaseOverview = as(scopedToA, cost,
                () -> purchaseReportService.overview(purchaseForm()));
        assertThat(purchaseOverview.getSubmittedOrderCount()).isEqualTo(2L);
        assertThat(purchaseOverview.getSubmittedAmount()).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
        assertThat(purchaseOverview.getConfirmedReceiptCount()).isEqualTo(2L);
        assertThat(purchaseOverview.getPendingPutawayReceiptCount()).isEqualTo(1L);
        assertThat(purchaseOverview.getPurchaseInCostAmount()).as("只有 DIRECT 那一笔入了账")
                .isEqualByComparingTo(WAREHOUSE_INBOUND_COST);

        ReportOverviewVO overview = as(scopedToA, cost, () -> overviewReportService.overview(overviewForm()));
        assertThat(overview.getSubmittedPurchaseAmount()).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
        assertThat(overview.getPurchaseInCostAmount()).isEqualByComparingTo(WAREHOUSE_INBOUND_COST);
        assertThat(overview.getInventoryBookValue()).isNotNull();
        assertThat(overview.getStockedSkuCount()).isEqualTo(1L);

        assertThat(as(scopedToA, cost, () -> purchaseReportService.byProduct(purchaseForm()).getList()))
                .as("按商品维度也只剩甲仓的 SKU")
                .extracting(PurchaseReportVO.ProductRow::getSkuCode).containsExactly(factsA.skuCode());
        assertThat(as(scopedToA, cost, () -> purchaseReportService.bySupplier(purchaseForm()).getList()))
                .hasSize(1);
        assertThat(as(scopedToA, cost, () -> purchaseReportService.byPurchaser(purchaseForm()).getList()))
                .hasSize(1);
        assertThat(as(scopedToA, cost, () -> purchaseReportService.itemList(purchaseForm()).getList()))
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.getWarehouseName()).isEqualTo(warehouseNameA));
        assertThat(as(scopedToA, cost, () -> purchaseReportService.priceTrend(purchaseForm())))
                .as("价格波动也按仓库收窄：甲仓只有自己的 SKU")
                .extracting(PurchaseReportVO.PriceTrendPoint::getSkuId).containsOnly(factsA.skuId());
        assertThat(as(scopedToA, cost,
                () -> purchaseReportService.topSupplierInbound(purchaseForm()))).hasSize(1);
        // 损耗两条语句共用 lossMovementWhere 片段：本仓没有损耗流水，但带 IN (?) 的 SQL
        // 仍必须在真实库里可解析可执行（本模块的可执行性门禁比行数断言更值钱）。
        assertThatCode(() -> as(scopedToA, cost, () -> {
            inventoryReportService.lossList(inventoryForm());
            return inventoryReportService.lossSummary(inventoryForm());
        })).doesNotThrowAnyException();
    }

    // ==================== 2. 聚合恒等：互斥的两半之和等于并集 ====================

    @Test
    @DisplayName("聚合恒等：甲仓金额 + 乙仓金额 = 双仓授权金额，且逐日轴与指标卡同一口径")
    void scopedAmountsAddUpToUnionScope() {
        Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);
        BigDecimal onlyA = as(scopedToA, cost, () -> purchaseReportService.overview(purchaseForm()))
                .getSubmittedAmount();
        BigDecimal onlyB = as(scopedToB, cost, () -> purchaseReportService.overview(purchaseForm()))
                .getSubmittedAmount();
        BigDecimal union = as(scopedToBoth, cost, () -> purchaseReportService.overview(purchaseForm()))
                .getSubmittedAmount();

        assertThat(onlyA).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
        assertThat(onlyB).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
        assertThat(union).isEqualByComparingTo(onlyA.add(onlyB));

        // 成本金额同样可加：它坐在 inventory_movement 的派生表里，是「改数字」风险最高的那一处
        BigDecimal costA = as(scopedToA, cost, () -> purchaseReportService.overview(purchaseForm()))
                .getPurchaseInCostAmount();
        BigDecimal costB = as(scopedToB, cost, () -> purchaseReportService.overview(purchaseForm()))
                .getPurchaseInCostAmount();
        BigDecimal costUnion = as(scopedToBoth, cost, () -> purchaseReportService.overview(purchaseForm()))
                .getPurchaseInCostAmount();
        assertThat(costUnion).isEqualByComparingTo(costA.add(costB));

        // 条数恒等：范围谓词漏到维度 JOIN 上时金额仍可能可加，条数不会
        long movementsA = as(scopedToA, cost, () -> inventoryReportService.movementList(inventoryForm()))
                .getTotal();
        long movementsB = as(scopedToB, cost, () -> inventoryReportService.movementList(inventoryForm()))
                .getTotal();
        long movementsUnion = as(scopedToBoth, cost, () -> inventoryReportService.movementList(inventoryForm()))
                .getTotal();
        assertThat(movementsA).isEqualTo(1L);
        assertThat(movementsUnion).isEqualTo(movementsA + movementsB).isGreaterThan(movementsA);

        // 指标卡与趋势/每日统计共用一条 SQL：同一范围下两边那个数必须相等
        List<ReportDailyStatVO> days = as(scopedToA, cost, () -> overviewReportService.dailyStat(overviewForm()));
        BigDecimal axisPurchaseAmount = days.stream().map(ReportDailyStatVO::getSubmittedPurchaseAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(axisPurchaseAmount).isEqualByComparingTo(
                as(scopedToA, cost, () -> overviewReportService.overview(overviewForm()))
                        .getSubmittedPurchaseAmount());

        // 台账恒等式（余额 = 本仓流水方向净额）在收口后仍然成立：范围只读，不改账本
        assertLedgerBalanced(warehouseA, factsA.skuId());
        assertLedgerBalanced(warehouseB, factsB.skuId());
    }

    // ==================== 3. 导出与页面同一套范围 ====================

    @Test
    @DisplayName("Excel 导出与页面查询同一范围：导出的工作簿里有甲仓无乙仓")
    void exportedWorkbookUsesTheSameWarehouseScopeAsThePage() throws Exception {
        Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);

        assertThat(export(scopedToA, cost, response -> reportController.exportMovement(inventoryForm(), response)))
                .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
        assertThat(export(scopedToA, cost, response -> reportController.exportInbound(receiptForm(), response)))
                .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
        assertThat(export(scopedToA, cost, response -> reportController.exportReceipt(receiptForm(), response)))
                .contains(receiptNo(factsA.inboundReceiptId()))
                .doesNotContain(receiptNo(factsB.inboundReceiptId()));
        assertThat(export(scopedToA, cost,
                response -> reportController.exportPurchaseItem(purchaseForm(), response)))
                .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
        assertThat(export(scopedToA, cost,
                response -> reportController.exportPurchaseProduct(purchaseForm(), response)))
                .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
        assertThat(export(scopedToA, cost,
                response -> reportController.exportInventoryValue(inventoryForm(), response)))
                .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());

        // 授权双仓的人导出两仓：证明收窄来自范围本身，不是导出被写死成半张表
        assertThat(export(scopedToBoth, cost,
                response -> reportController.exportMovement(inventoryForm(), response)))
                .contains(factsA.skuCode(), factsB.skuCode());
    }

    @Test
    @DisplayName("无成本权限时导出的成本单元格是空、接口字段是 null，绝不写成 0")
    void exportMasksCostCellsInsteadOfWritingZero() throws Exception {
        String withCost = export(scopedToA, Set.of(ScmReportAccess.COST_QUERY_PERM),
                response -> reportController.exportMovement(inventoryForm(), response));
        String withoutCost = export(scopedToA, Set.of(),
                response -> reportController.exportMovement(inventoryForm(), response));

        assertThat(withCost).contains(DIRECT_PRICE);
        assertThat(withoutCost).doesNotContain(DIRECT_PRICE)
                .as("抹除只抹成本列，数量列仍是事实").contains(DIRECT_QUANTITY, factsA.skuCode());
        assertThat(as(scopedToA, Set.of(),
                () -> inventoryReportService.movementList(inventoryForm()).getList().getFirst().getUnitCost()))
                .isNull();
    }

    // ==================== 4. 无授权范围：空结果，不是一堆 0 ====================

    @Test
    @DisplayName("无任何仓库授权：列表是空分页、汇总是 null，绝不给出别的仓的数字")
    void callerWithoutWarehouseGrantSeesEmptyResultNotZeros() throws Exception {
        Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);

        PageResult<InventoryReportVO.MovementRow> movements = as(withoutAnyWarehouse, cost,
                () -> inventoryReportService.movementList(inventoryForm()));
        assertThat(movements.getList()).isEmpty();
        assertThat(movements.getTotal()).isZero();
        assertThat(movements.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必另写分支").isTrue();

        assertThat(as(withoutAnyWarehouse, cost, () -> inventoryReportService.lossList(inventoryForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> inventoryReportService.valueList(inventoryForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> inventoryReportService.flowSummary(inventoryForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> receiptReportService.receiptList(receiptForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> receiptReportService.inboundList(receiptForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost,
                () -> receiptReportService.pendingPutawayList(receiptForm()).getList())).isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.itemList(purchaseForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.byProduct(purchaseForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.bySupplier(purchaseForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.byPurchaser(purchaseForm()).getList()))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.priceTrend(purchaseForm())))
                .isEmpty();
        assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.topSupplierInbound(purchaseForm())))
                .isEmpty();

        PurchaseReportVO.Overview emptyPurchase = as(withoutAnyWarehouse, cost,
                () -> purchaseReportService.overview(purchaseForm()));
        assertThat(emptyPurchase.getSubmittedAmount()).as("无授权仓库时采购承诺是未知，不是 0").isNull();
        assertThat(emptyPurchase.getSubmittedOrderCount()).isNull();
        assertThat(emptyPurchase.getConfirmedReceiptCount()).isNull();
        assertThat(emptyPurchase.getPendingPutawayReceiptCount()).isNull();

        InventoryReportVO.LossSummary emptyLoss = as(withoutAnyWarehouse, cost,
                () -> inventoryReportService.lossSummary(inventoryForm()));
        assertThat(emptyLoss.getStocktakeLossCount()).isNull();
        assertThat(emptyLoss.getLossReportCount()).isNull();
        assertThat(emptyLoss.getTotalLossCostAmount()).isNull();

        ReportOverviewVO overview = as(withoutAnyWarehouse, cost,
                () -> overviewReportService.overview(overviewForm()));
        assertThat(overview.getSubmittedPurchaseAmount()).isNull();
        assertThat(overview.getPurchaseInCostAmount()).isNull();
        assertThat(overview.getInventoryBookValue()).isNull();
        assertThat(overview.getStockedSkuCount()).isNull();
        // 日期轴上每一行的采购列按同一规则抹除，否则卡片是 — 而折线是 0
        assertThat(as(withoutAnyWarehouse, cost, () -> overviewReportService.dailyStat(overviewForm())))
                .allSatisfy(row -> assertThat(row.getSubmittedPurchaseAmount()).isNull());

        String workbook = export(withoutAnyWarehouse, cost,
                response -> reportController.exportMovement(inventoryForm(), response));
        assertThat(workbook).doesNotContain(factsA.skuCode(), factsB.skuCode())
                .as("工作簿仍能写出（表头在），但没有任何一行数据").contains("发生时间");
    }

    // ==================== 5. 销售侧没有仓库事实，故不被仓库范围收窄 ====================

    @Test
    @DisplayName("销售侧无仓库列：仓库范围为空的人也照常看到销售额，可见性只由页面权限承担")
    void salesReportsStayUnscopedBecauseOrdersCarryNoWarehouse() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("RPS");
        confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");

        ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        form.setCustomerId(customerId);

        for (Long employeeId : List.of(scopedToA, withoutAnyWarehouse)) {
            assertThat(as(employeeId, Set.of(), () -> salesReportService.byCustomer(form).getList()))
                    .as("员工 %s 只是没有仓库授权，销售事实本身没有仓库列", employeeId)
                    .anySatisfy(row -> assertThat(row.getCustomerId()).isEqualTo(customerId));
        }
        assertThat(as(withoutAnyWarehouse, Set.of(), () -> salesReportService.itemList(form).getList())).hasSize(1);
        assertThat(as(withoutAnyWarehouse, Set.of(), () -> salesReportService.byProduct(form).getList())).hasSize(1);
        assertThat(as(withoutAnyWarehouse, Set.of(), () -> salesReportService.bySeller(form).getList()))
                .as("按销售员维度也不引入仓库替代维度").isNotEmpty();

        // 概览里不受仓库范围影响的两个销售指标仍要有值，否则说明范围把不该收的也收了
        ReportOverviewVO overview = as(withoutAnyWarehouse, Set.of(),
                () -> overviewReportService.overview(overviewForm()));
        assertThat(overview.getConfirmedOrderCount()).isNotNull();
        assertThat(overview.getConfirmedOrderAmount()).isNotNull();
    }

    // ==================== 6. 超管与「查看全部仓库」权限 ====================

    @Test
    @DisplayName("administratorFlag=true 不受影响：两个仓的流水同页可见，数字未被收窄")
    void administratorStillSeesEveryWarehouse() {
        List<InventoryReportVO.MovementRow> rows = inventoryReportService.movementList(inventoryForm()).getList();
        assertThat(rows).extracting(InventoryReportVO.MovementRow::getSkuCode)
                .contains(factsA.skuCode(), factsB.skuCode());
        assertThat(rows).extracting(InventoryReportVO.MovementRow::getWarehouseId)
                .contains(warehouseA, warehouseB);

        PurchaseReportVO.Overview overview = purchaseReportService.overview(purchaseForm());
        assertThat(overview.getSubmittedAmount())
                .as("超管至少看到两个测试仓之和").isGreaterThanOrEqualTo(WAREHOUSE_PURCHASE_AMOUNT.multiply(
                        BigDecimal.valueOf(2)));
        assertThat(overview.getPurchaseInCostAmount()).isNull();
        // 无 Sa-Token 上下文时成本失败关闭，与范围无关：证明两件事互不隐含
        assertThat(overview.getSubmittedAmount()).isNotNull();
    }

    @Test
    @DisplayName("「查看全部仓库」是显式权限：没有授权行但持该权限者可见两仓")
    void warehouseAllPermissionIsAnExplicitScopeNotADefault() {
        assertThat(as(withoutAnyWarehouse, Set.of(),
                () -> inventoryReportService.movementList(inventoryForm()).getList()))
                .as("没权限也没授权行 ⇒ 空").isEmpty();
        assertThat(as(withoutAnyWarehouse, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> inventoryReportService.movementList(inventoryForm()).getList()))
                .extracting(InventoryReportVO.MovementRow::getSkuCode)
                .contains(factsA.skuCode(), factsB.skuCode());
    }

    // ==================== 夹具 ====================

    /**
     * 在一个仓库里造齐报表要用的事实：一单 DIRECT 收货确认（产生 {@code PURCHASE_IN} 流水与余额），
     * 另加一单 {@code WAREHOUSE_CONFIRM} 收货确认（只挂待入库、不写库存），
     * 于是收货页有 2 行、入库页 1 行、待入库 1 行、流水与余额各 1 行。
     */
    private WarehouseFacts stockWarehouse(Long warehouseId, String tag) {
        Long skuId = newOnShelfSku(tag);
        Long supplierId = newPurchasableSupplier(tag, skuId);

        PurchaseOrderVO inbound = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, DIRECT_QUANTITY, DIRECT_PRICE),
                prefix + ":" + tag + ":inbound:po");
        PurchaseReceiptVO inboundReceipt = submittedOrderReceipt(inbound.getId());
        confirmReceipt(inboundReceipt.getId(), DIRECT_QUANTITY);

        PurchaseOrderVO pending = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, PENDING_QUANTITY, PENDING_PRICE),
                prefix + ":" + tag + ":pending:po");
        submitOrder(pending.getId());
        PurchaseReceiptVO pendingReceipt = createWarehouseConfirmReceipt(pending.getId(), tag);
        confirmReceipt(pendingReceipt.getId(), PENDING_QUANTITY);

        return new WarehouseFacts(warehouseId, skuId, skuCodeOf(skuId), inbound.getId(), pending.getId(),
                inboundReceipt.getId(), pendingReceipt.getId());
    }

    /** {@code WAREHOUSE_CONFIRM} 的收货单：确认后 {@code putaway_status} 保持 PENDING，不写库存。 */
    private PurchaseReceiptVO createWarehouseConfirmReceipt(Long orderId, String tag) {
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
        form.setRemark("报表数据范围 IT 待入库");
        return purchaseReceiptService.create(form, prefix + ":wc:" + tag + ":" + orderId);
    }

    /**
     * 真实员工行，{@code administrator_flag} 必须 FALSE（裁决第 5 条）。
     */
    private Long newEmployee(String suffix) {
        String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                "报表范围" + suffix);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("正式业务角色的验收账号禁止超管位").isFalse();
        return employeeId;
    }

    private void grantWarehouseScope(Long employeeId, Long warehouseId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouseId);
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("报表数据范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    /** 以某个员工 + 一组功能权限执行一段逻辑；未点名的权限码取 false（失败关闭）。不可嵌套。 */
    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            // 同一批语句会换身份重跑，MyBatis 一级缓存必须清，否则断言读回的是上一个人的结果
            evictMybatisCache();
            return body.get();
        }
    }

    /** 跑一个导出端点，并把工作簿（zip 容器）里的全部 XML 拼成一段文本供包含性断言。 */
    private String export(Long employeeId, Set<String> permissions, ExportCall call) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        as(employeeId, permissions, () -> {
            try {
                call.run(response);
            } catch (Exception e) {
                throw new IllegalStateException("导出端点执行失败", e);
            }
            return null;
        });
        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes.length).as("零行结果也必须写出工作簿容器，而不是一个空响应").isGreaterThan(2);
        assertThat(new String(bytes, 0, 2, StandardCharsets.ISO_8859_1)).as("导出的必须是 xlsx").isEqualTo("PK");
        return workbookText(bytes);
    }

    interface ExportCall {
        void run(jakarta.servlet.http.HttpServletResponse response) throws Exception;
    }

    /**
     * 按文本断言「某个编码在 / 不在工作簿里」，比引入 Excel 读回模型更省，也不需要知道列在第几格 ——
     * 本类只关心哪些仓的行进了这张表。
     *
     * <p>必须走 {@link ZipFile} 而不是 {@link ZipInputStream}：FastExcel 写出的 entry 带 data
     * descriptor，本地文件头里的 size 先落 0、真长度写在数据之后，流式解析器按本地头判定就会抛
     * {@code invalid entry size}。{@code ZipFile} 读的是中央目录，那里才是写完的真实长度；
     * 代价是它需要随机访问，所以先落成临时文件。
     */
    private String workbookText(byte[] bytes) throws IOException {
        Path workbook = workbookDir.resolve("export.xlsx");
        Files.write(workbook, bytes);
        StringBuilder text = new StringBuilder();
        try (ZipFile zip = new ZipFile(workbook.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (InputStream content = zip.getInputStream(entry)) {
                        text.append(new String(content.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return text.toString();
    }

    private String skuCodeOf(Long skuId) {
        return jdbc.queryForObject("SELECT sku_code FROM product_sku WHERE id = ?", String.class, skuId);
    }

    private String receiptNo(Long receiptId) {
        return jdbc.queryForObject("SELECT receipt_no FROM purchase_receipt WHERE id = ?", String.class,
                receiptId);
    }

    private String warehouseName(Long warehouseId) {
        return jdbc.queryForObject("SELECT name FROM warehouse WHERE id = ?", String.class, warehouseId);
    }

    private LocalDate end() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private LocalDate start() {
        return end().minusDays(29);
    }

    private <T extends PageParam> T page(T form) {
        form.setPageNum(1L);
        form.setPageSize(200L);
        return form;
    }

    private ScmOverviewReportQueryForm overviewForm() {
        ScmOverviewReportQueryForm form = new ScmOverviewReportQueryForm();
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }

    private ScmPurchaseReportQueryForm purchaseForm() {
        ScmPurchaseReportQueryForm form = page(new ScmPurchaseReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }

    private ScmReceiptReportQueryForm receiptForm() {
        ScmReceiptReportQueryForm form = page(new ScmReceiptReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }

    private ScmInventoryReportQueryForm inventoryForm() {
        ScmInventoryReportQueryForm form = page(new ScmInventoryReportQueryForm());
        form.setStartDate(start());
        form.setEndDate(end());
        return form;
    }
}
