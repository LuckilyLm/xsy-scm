package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseDemandService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购需求生成（W5 Target Design §11.2，8 例）。
 *
 * <p>覆盖三条契约：
 * <ul>
 *   <li><b>Q6a</b>：`demand_date` = `source_confirmed_at` 在 Asia/Shanghai 下的日期，
 *       **不是**窗口起点（`date(startAt)`）；并由 V15 的 `ck_purchase_demand_date` 在库层复核；</li>
 *   <li><b>Q17</b>：`demand_unit_snapshot` 来自 `sales_order_item.sale_unit_snapshot`（销售单位），
 *       与采购单位（`supplier_sku.purchase_unit`）是两个独立快照；</li>
 *   <li><b>Q14 + C1</b>：`DEMAND_GENERATE` 日志的两个 id 必须同时为空；
 *       同一来源行的重复汇总靠 `uk_purchase_demand_source_active` + `ON CONFLICT DO NOTHING` 收敛。</li>
 * </ul>
 *
 * <p><b>关于「并发 generate」</b>：本用例跑在一个 Spring 事务里，`generate` 的**来源行**
 * （`sales_order` / `sales_order_item`）是同一事务内新建的、对其它连接不可见，
 * 因此无法用两个线程真并发地驱动 `generate`（另一个线程查不到来源行）。
 * 这里改为**确定性地验证收敛机制本身**：唯一索引挡住第二条活动需求
 * （{@link #sourceUniqueIndexBlocksSecondActiveDemand}），
 * 且 `insertIgnore` 在冲突时返回 0、重读收敛到同一行
 * （{@link #insertCompetitionConvergesToSingleActiveDemand}）——
 * 这两点正是 C1 在并发下唯一会走到的分支。
 */
@DisplayName("采购需求生成：来源快照 / Q6a / Q17 / 去重收敛（PG IT）")
class PurchaseDemandServiceIT extends ScmW5PgITBase {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private PurchaseDemandService demandService;

    @Autowired
    private PurchaseQueryService queryService;

    @Autowired
    private PurchaseDemandDao purchaseDemandDao;

    @Autowired
    private PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    /**
     * 紧贴该订单确认时刻的 1 秒窗口。
     *
     * <p>窗口必须**紧**：开发库里已经有历史 CONFIRMED 订单（W4 验收遗留），
     * 宽窗口会把它们的来源行一起汇总进来，让 `createdCount` 失去可断言性。
     */
    private PurchaseDemandGenerateForm window(Fixture fixture) {
        PurchaseDemandGenerateForm form = new PurchaseDemandGenerateForm();
        form.setStartAt(fixture.confirmedAt());
        form.setEndAt(fixture.confirmedAt().plusSeconds(1));
        form.setWarehouseId(seedWarehouseId());
        form.setSupplierId(fixture.supplierId());
        return form;
    }

    private PurchaseDemandEntity myDemand(Fixture fixture) {
        List<PurchaseDemandEntity> rows =
                purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId()));
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    // ------------------------------------------------------------------
    // 1. 快照与来源口径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("generate：来源已确认订单 → 需求快照齐全，需求量取实数量而不是订购量")
    void generateCreatesDemandFromConfirmedOrderWithSnapshots() {
        Fixture fixture = fixture("DG1", "5.0000", "3.2500");
        PurchaseDemandGenerateForm form = window(fixture);
        form.setPurchaserId(anyEmployeeId());

        PurchaseDemandService.GenerateResult result = demandService.generate(form, prefix + ":g1");

        assertThat(result.getSourceLineCount()).isEqualTo(1);
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getDemandIds()).hasSize(1);

        PurchaseDemandEntity row = myDemand(fixture);
        assertThat(row.getId()).isEqualTo(result.getDemandIds().getFirst());
        assertThat(row.getSalesOrderId()).isEqualTo(fixture.salesOrderId());
        assertThat(row.getSkuId()).isEqualTo(fixture.skuId());
        assertThat(row.getSalesOrderNoSnapshot()).isNotBlank();
        assertThat(row.getSpuCodeSnapshot()).isNotBlank();
        assertThat(row.getSkuCodeSnapshot()).isNotBlank();
        // 需求量 = sales_order_item.actual_quantity（A 源口径），不是 ordered_quantity = 5.0000
        assertThat(row.getRequiredQuantity()).isEqualByComparingTo("3.2500");
        assertThat(row.getAllocatedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getStatus()).isEqualTo("PENDING");
        // Q17：需求单位 = 销售单位（基类造的 SKU 一律 kg），且**不是**采购单位之外的别的东西
        assertThat(row.getDemandUnitSnapshot()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(row.getSupplierId()).isEqualTo(fixture.supplierId());
        assertThat(row.getPurchaserId()).isEqualTo(anyEmployeeId());
        assertThat(row.getWarehouseId()).isEqualTo(seedWarehouseId());
        assertThat(row.getSourceConfirmedAt().toInstant()).isEqualTo(fixture.confirmedAt().toInstant());
    }

    // ------------------------------------------------------------------
    // 2. Q6a：demand_date 派生
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q6a：demand_date 取 source_confirmed_at 的 Asia/Shanghai 日期，且库层 CHECK 成立")
    void demandDateComesFromSourceConfirmedAtAndDbCheckHolds() {
        Fixture fixture = fixture("DG2", "4.0000", "2.5000");

        // 故意把窗口起点拉到 1 天前：若实现写成 date(startAt)，demand_date 会差一天
        PurchaseDemandGenerateForm form = window(fixture);
        form.setStartAt(fixture.confirmedAt().minusDays(1));
        demandService.generate(form, prefix + ":g2");

        PurchaseDemandEntity row = myDemand(fixture);
        LocalDate expected = fixture.confirmedAt().atZoneSameInstant(SHANGHAI).toLocalDate();
        assertThat(row.getDemandDate()).isEqualTo(expected);
        assertThat(row.getDemandDate())
                .isNotEqualTo(form.getStartAt().atZoneSameInstant(SHANGHAI).toLocalDate());
        // 原订单确认时间原样保存，不被窗口起点污染
        assertThat(row.getSourceConfirmedAt().toInstant()).isEqualTo(fixture.confirmedAt().toInstant());

        // 库层复核：V15 的 ck_purchase_demand_date 与 Java 侧派生口径必须逐行一致
        Integer mismatched = jdbc.queryForObject(
                "SELECT count(*) FROM purchase_demand "
                        + "WHERE demand_date <> (source_confirmed_at AT TIME ZONE 'Asia/Shanghai')::date",
                Integer.class);
        assertThat(mismatched).isZero();
    }

    // ------------------------------------------------------------------
    // 3. 半开区间
    // ------------------------------------------------------------------

    @Test
    @DisplayName("窗口是半开区间 [startAt, endAt)：endAt 恰好等于确认时刻时取不到该行")
    void windowIsHalfOpen() {
        Fixture fixture = fixture("DG3", "2.0000", "1.0000");

        PurchaseDemandGenerateForm form = new PurchaseDemandGenerateForm();
        form.setStartAt(fixture.confirmedAt().plusSeconds(1));
        form.setEndAt(fixture.confirmedAt().plusSeconds(2));
        form.setWarehouseId(seedWarehouseId());

        PurchaseDemandService.GenerateResult result = demandService.generate(form, prefix + ":g3");

        assertThat(result.getSourceLineCount()).isZero();
        assertThat(result.getCreatedCount()).isZero();
        assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId())))
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // 4. 重复汇总（幂等重放 + 去重）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("重复 generate：同一来源行收敛到同一需求，第二次计为 skipped 而不是失败")
    void regenerateSkipsExistingSourceLinesAndReturnsSameDemandId() {
        Fixture fixture = fixture("DG4", "6.0000", "4.0000");

        PurchaseDemandService.GenerateResult first =
                demandService.generate(window(fixture), prefix + ":g4a");
        // 不同幂等键（不同请求身份）：走「已存在 → 跳过」分支，而不是重放
        PurchaseDemandService.GenerateResult second =
                demandService.generate(window(fixture), prefix + ":g4b");

        assertThat(second.getCreatedCount()).isZero();
        assertThat(second.getSkippedCount()).isEqualTo(1);
        assertThat(second.getDemandIds()).containsExactlyElementsOf(first.getDemandIds());
        assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId())))
                .hasSize(1);
    }

    // ------------------------------------------------------------------
    // 5. C1：INSERT 竞争收敛
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C1：ON CONFLICT DO NOTHING 返回 0，重读收敛到同一条活动需求")
    void insertCompetitionConvergesToSingleActiveDemand() {
        Fixture fixture = fixture("DG5", "3.0000", "2.0000");
        demandService.generate(window(fixture), prefix + ":g5");
        PurchaseDemandEntity winner = myDemand(fixture);

        // 模拟「另一个连接抢先插入了同一来源行」：复制业务字段、不带 id，再插一次
        PurchaseDemandEntity competitor = new PurchaseDemandEntity();
        competitor.setSalesOrderId(winner.getSalesOrderId());
        competitor.setSalesOrderItemId(winner.getSalesOrderItemId());
        competitor.setSpuId(winner.getSpuId());
        competitor.setSkuId(winner.getSkuId());
        competitor.setSalesOrderNoSnapshot(winner.getSalesOrderNoSnapshot());
        competitor.setSpuCodeSnapshot(winner.getSpuCodeSnapshot());
        competitor.setProductNameSnapshot(winner.getProductNameSnapshot());
        competitor.setSkuCodeSnapshot(winner.getSkuCodeSnapshot());
        competitor.setSkuNameSnapshot(winner.getSkuNameSnapshot());
        competitor.setSpecValuesSnapshot(winner.getSpecValuesSnapshot());
        competitor.setDemandUnitSnapshot(winner.getDemandUnitSnapshot());
        competitor.setProductTypeSnapshot(winner.getProductTypeSnapshot());
        competitor.setRequiredQuantity(winner.getRequiredQuantity());
        competitor.setAllocatedQuantity(winner.getAllocatedQuantity());
        competitor.setWarehouseId(winner.getWarehouseId());
        competitor.setStatus(winner.getStatus());
        competitor.setSourceConfirmedAt(winner.getSourceConfirmedAt());
        competitor.setDemandDate(winner.getDemandDate());
        competitor.setCreatedBy("competitor");

        assertThat(purchaseDemandDao.insertIgnore(competitor)).isZero();
        assertThat(competitor.getId()).isNull();   // 冲突时 JDBC 不回传生成键 → 调用方必须走重读分支
        assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId())))
                .singleElement()
                .extracting(PurchaseDemandEntity::getId)
                .isEqualTo(winner.getId());
    }

    @Test
    @DisplayName("uk_purchase_demand_source_active：第二条活动需求被唯一索引直接拒绝")
    void sourceUniqueIndexBlocksSecondActiveDemand() {
        Fixture fixture = fixture("DG6", "3.0000", "2.0000");
        demandService.generate(window(fixture), prefix + ":g6");
        PurchaseDemandEntity winner = myDemand(fixture);

        expectSqlFailure(
                "INSERT INTO purchase_demand (sales_order_id, sales_order_item_id, spu_id, sku_id, "
                        + "sales_order_no_snapshot, spu_code_snapshot, product_name_snapshot, sku_code_snapshot, "
                        + "sku_name_snapshot, demand_unit_snapshot, product_type_snapshot, required_quantity, "
                        + "allocated_quantity, warehouse_id, status, source_confirmed_at, demand_date, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'PENDING', ?, ?, 'dup')",
                winner.getSalesOrderId(), winner.getSalesOrderItemId(), winner.getSpuId(), winner.getSkuId(),
                winner.getSalesOrderNoSnapshot(), winner.getSpuCodeSnapshot(), winner.getProductNameSnapshot(),
                winner.getSkuCodeSnapshot(), winner.getSkuNameSnapshot(), winner.getDemandUnitSnapshot(),
                winner.getProductTypeSnapshot(), winner.getRequiredQuantity(), winner.getWarehouseId(),
                winner.getSourceConfirmedAt(), winner.getDemandDate());
    }

    // ------------------------------------------------------------------
    // 6. Q14：日志归属
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q14：DEMAND_GENERATE 日志的 purchase_order_id 与 purchase_receipt_id 必须同时为空")
    void generateWritesDemandGenerateLogWithBothIdsNull() {
        Fixture fixture = fixture("DG7", "2.0000", "1.0000");
        demandService.generate(window(fixture), prefix + ":g7");

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT purchase_order_id, purchase_receipt_id, operation_type, operator, after_data "
                        + "FROM purchase_operation_log WHERE operation_type = 'DEMAND_GENERATE' "
                        + "ORDER BY id DESC LIMIT 1");
        assertThat(log.get("purchase_order_id")).isNull();
        assertThat(log.get("purchase_receipt_id")).isNull();
        assertThat(log.get("operation_type")).isEqualTo("DEMAND_GENERATE");
        // 操作者落的是 `userType:employeeId`（ScmOperator 的口径），不是员工姓名
        assertThat(log.get("operator")).isEqualTo(ScmOperator.current());
        assertThat(String.valueOf(log.get("after_data"))).contains("createdCount");
    }

    // ------------------------------------------------------------------
    // 7. 前置校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("窗口非法 → 40080；仓库已停用 → 40987（且窗口校验先于仓库校验）")
    void generateRejectsBadWindowAndDisabledWarehouse() {
        Fixture fixture = fixture("DG8", "2.0000", "1.0000");

        PurchaseDemandGenerateForm reversed = window(fixture);
        reversed.setEndAt(reversed.getStartAt());     // startAt == endAt：空集是调用方 bug，不是「无数据」
        expectCode(() -> demandService.generate(reversed, prefix + ":g8a"), 40080);

        PurchaseDemandGenerateForm nullWindow = window(fixture);
        nullWindow.setEndAt(null);
        expectCode(() -> demandService.generate(nullWindow, prefix + ":g8b"), 40080);

        Long disabled = newWarehouse("DISABLED");
        disableWarehouse(disabled);
        PurchaseDemandGenerateForm disabledWarehouse = window(fixture);
        disabledWarehouse.setWarehouseId(disabled);
        expectCode(() -> demandService.generate(disabledWarehouse, prefix + ":g8c"), 40987);
    }

    // ------------------------------------------------------------------
    // 8. 只读查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("需求列表与详情：unallocatedQuantity = required − allocated，口径与列表一致")
    void demandQueryAndDetailExposeUnallocatedQuantity() {
        Fixture fixture = fixture("DG9", "7.0000", "5.0000");
        demandService.generate(window(fixture), prefix + ":g9");
        PurchaseDemandEntity row = myDemand(fixture);

        PurchaseDemandQueryForm query = new PurchaseDemandQueryForm();
        query.setSalesOrderNo(row.getSalesOrderNoSnapshot());
        // 分页参数必须显式给：PageParam 默认 null，convert2PageQuery 会 NPE
        query.setPageNum(1L);
        query.setPageSize(10L);
        var page = queryService.demandQuery(query);
        assertThat(page.getList()).isNotEmpty();
        assertThat(page.getList()).allSatisfy(vo -> assertThat(vo.getUnallocatedQuantity())
                .isEqualByComparingTo(vo.getRequiredQuantity().subtract(vo.getAllocatedQuantity())));

        var detail = queryService.demandDetail(row.getId());
        assertThat(detail.getUnallocatedQuantity()).isEqualByComparingTo("5.0000");
        assertThat(detail.getDemandUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(detail.getProductType()).isEqualTo("NON_STANDARD");
        // 尚未分配：没有任何 allocation 行
        assertThat(purchaseDemandAllocationDao.listActiveByDemandIds(List.of(row.getId()))).isEmpty();
        // 生成器的快照字段与详情投影必须逐字段同源（Q17 的 demandUnit 就是快照列）
        assertThat(detail.getSpecValues())
                .isEqualTo(PurchaseSnapshotFactory.copySpecValues(row.getSpecValuesSnapshot()));
    }
}
