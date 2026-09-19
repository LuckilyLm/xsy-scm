package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryBalanceQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryMovementQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryBalanceVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryMovementVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购入库（{@code PURCHASE_IN}）的实时路径（W6 Target Design §12.1 #1 / #2 / #4 / #7 / #12 / #16 / #18）。
 *
 * <p><b>本类只覆盖「一笔入库是怎么落地的」</b>：同事务、多行、幂等重放、源身份防重、
 * 单位一致累加、可用量探测、以及两个只读查询页的行为。真正的**回滚原子性**（#3 / #13）与
 * **并发**（#5 / #6）在各自的类里，因为它们必须关掉测试的外层事务才能被真实观测。
 *
 * <p><b>为什么每个用例都用新 SKU</b>：库存余额的粒度是 {@code (warehouse_id, sku_id)}，
 * 用新 SKU 就等于拿到一个**零基线**，断言不必减去其它用例或开发库的存量。
 */
@DisplayName("W6 采购入库实时路径（PG IT）")
class ScmInventoryInboundIT extends ScmW6PgITBase {

    private InventoryBalanceQueryForm balanceQuery(Long warehouseId, Long skuId) {
        InventoryBalanceQueryForm form = new InventoryBalanceQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setWarehouseId(warehouseId);
        form.setSkuId(skuId);
        return form;
    }

    private InventoryMovementQueryForm movementQuery(Long warehouseId, Long skuId) {
        InventoryMovementQueryForm form = new InventoryMovementQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setWarehouseId(warehouseId);
        form.setSkuId(skuId);
        return form;
    }

    // ------------------------------------------------------------------
    // #1
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#1 confirm → PURCHASE_IN：余额增量、流水恒等式、源身份四元组（同事务）")
    void confirmLandsPurchaseInMovementAndBalance() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("IN1");
        W6Fixture fx = inboundFixture("IN1", skuId, "10.0000");

        PurchaseReceiptVO confirmed = confirmReceipt(fx.receipt().getId(), "10.0000");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");

        // 余额：恰好一行、数量 = 本次有效数量、单位 = 采购单位快照
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
        InventoryBalanceEntity balance = balanceRow(warehouseId, skuId);
        assertThat(balance.getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
        // version 从 0 起，一次自增 = 1（纵深防御列确实在动）
        assertThat(balance.getVersion()).isEqualTo(1);

        // 流水：恰好一条，四元组 + before/after 恒等式 + 成本快照（Q3）
        List<Map<String, Object>> movements = movementsOf(warehouseId, skuId);
        assertThat(movements).hasSize(1);
        Map<String, Object> movement = movements.getFirst();
        assertThat(movement.get("movement_type")).isEqualTo("PURCHASE_IN");
        assertThat(movement.get("source_document_type"))
                .isEqualTo(PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE);
        assertThat(movement.get("source_document_id")).isEqualTo(fx.receipt().getId());
        assertThat(movement.get("source_document_item_id")).isEqualTo(fx.receiptItemId());
        assertThat((BigDecimal) movement.get("quantity")).isEqualByComparingTo("10.0000");
        assertThat((BigDecimal) movement.get("before_quantity")).isEqualByComparingTo("0.0000");
        assertThat((BigDecimal) movement.get("after_quantity")).isEqualByComparingTo("10.0000");
        assertThat(movement.get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat((BigDecimal) movement.get("unit_cost")).isEqualByComparingTo("6.2000");
        // append-only：deleted 恒为 FALSE（Q7）
        assertThat(movement.get("deleted")).isEqualTo(false);
        assertThat(movementsOfReceiptItem(fx.receiptItemId())).isEqualTo(1);

        // 只读查询面立即可见（余额是活状态，展示字段实时联表）
        PageResult<InventoryBalanceVO> balancePage =
                inventoryBalanceQueryService.query(balanceQuery(warehouseId, skuId));
        assertThat(balancePage.getList()).hasSize(1);
        assertThat(balancePage.getList().getFirst().getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(balancePage.getList().getFirst().getQuantity()).isEqualByComparingTo("10.0000");

        PageResult<InventoryMovementVO> movementPage =
                inventoryMovementQueryService.query(movementQuery(warehouseId, skuId));
        assertThat(movementPage.getList()).hasSize(1);
        // Q9：人类可读来源 = 收货单号（没有 movement_no）
        assertThat(movementPage.getList().getFirst().getReceiptNo()).isEqualTo(confirmed.getReceiptNo());
    }

    // ------------------------------------------------------------------
    // #2
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#2 多行收货（标品 + 非标品）：每行一条流水，非标品数量 = 实重")
    void multiLineReceiptWritesOneMovementPerLine() {
        Long warehouseId = seedWarehouseId();
        Long standardSku = newSkuOfType("IN2S", "STANDARD", "ON_SHELF");
        Long nonStandardSku = newSkuOfType("IN2N", "NON_STANDARD", "ON_SHELF");
        Long supplierId = newSupplier("IN2");
        // 一次 replace 挂两个 SKU（不能循环调用：replace 的语义是整体替换）
        linkSupplierSkus(supplierId, standardSku, nonStandardSku);

        PurchaseOrderAddForm form = orderForm(supplierId, warehouseId, standardSku, "4.0000", "3.0000");
        form.getItems().add(item(nonStandardSku, "6.0000", "5.0000"));
        PurchaseOrderVO order = purchaseOrderService.create(form, prefix + ":IN2:po");
        submitOrder(order.getId());
        PurchaseReceiptVO receipt = createReceipt(order.getId());

        List<PurchaseReceiptItemVO> lines = receiptItems(receipt.getId());
        assertThat(lines).hasSize(2);
        PurchaseReceiptItemVO standardLine = lineOf(lines, standardSku);
        PurchaseReceiptItemVO nonStandardLine = lineOf(lines, nonStandardSku);

        // 标品：实重三字段全空；非标品：实重必填（= 有效数量的来源）
        purchaseReceiptService.confirm(confirmForm(receipt.getId(), receipt.getVersion(),
                        receiptLine(standardLine.getId(), standardLine.getVersion(), "4.0000", null),
                        receiptLine(nonStandardLine.getId(), nonStandardLine.getVersion(), "6.0000", "6.0000")),
                prefix + ":IN2:confirm");

        // 每个 SKU 各一条流水、各一行余额
        assertThat(movementCount(warehouseId, standardSku)).isEqualTo(1);
        assertThat(movementCount(warehouseId, nonStandardSku)).isEqualTo(1);
        assertThat(balanceRow(warehouseId, standardSku).getQuantity()).isEqualByComparingTo("4.0000");
        // 非标品的有效数量取实重（声明数量也是 6，但取值来源是实重）
        assertThat(balanceRow(warehouseId, nonStandardSku).getQuantity()).isEqualByComparingTo("6.0000");
        assertThat(movementsOf(warehouseId, standardSku).getFirst().get("unit_snapshot"))
                .isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(movementsOf(warehouseId, nonStandardSku).getFirst().get("unit_snapshot"))
                .isEqualTo(DEFAULT_PURCHASE_UNIT);
    }

    private static PurchaseReceiptItemVO lineOf(List<PurchaseReceiptItemVO> lines, Long skuId) {
        return lines.stream()
                .filter(line -> line.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("收货单缺少 SKU " + skuId + " 的行"));
    }

    // ------------------------------------------------------------------
    // #4
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#4 confirm 幂等重放：同键同内容返回首次结果，流水仍只有 1 条")
    void confirmReplayDoesNotDoubleCountInventory() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("IN4");
        W6Fixture fx = inboundFixture("IN4", skuId, "10.0000");

        // 必须复用**同一个 form 实例**：W5 的幂等按「键 + 请求内容哈希」判定，
        // 重新读一次库拿到的 version 已经变了，会被判成「同键不同内容」(40990)。
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        PurchaseReceiptConfirmForm form = confirmForm(current.getId(), current.getVersion(),
                receiptLine(line.getId(), line.getVersion(), "10.0000"));
        String key = prefix + ":IN4:replay";

        PurchaseReceiptVO first = purchaseReceiptService.confirm(form, key);
        PurchaseReceiptVO replayed = purchaseReceiptService.confirm(form, key);

        assertThat(replayed.getId()).isEqualTo(first.getId());
        assertThat(replayed.getReceiptNo()).isEqualTo(first.getReceiptNo());
        assertThat(replayed.getVersion()).isEqualTo(first.getVersion());
        assertThat(replayed.getStatus()).isEqualTo("CONFIRMED");

        // 库存没有被重放加第二次
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
        // 采购侧累计也没有被加第二次
        assertThat(receivedQuantityOf(fx.orderItemId())).isEqualByComparingTo("10.0000");
    }

    // ------------------------------------------------------------------
    // #7
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#7 源身份防重：重复 postInbound 同 receiptItemId → 41002；部分唯一索引兜底")
    void duplicateSourceIdentityIsRejected() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("IN7");
        W6Fixture fx = inboundFixture("IN7", skuId, "5.0000");
        confirmReceipt(fx.receipt().getId(), "5.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

        // 命令侧：同一源事实再来一次 → 41002 fail-fast（**不静默跳过**，否则会出现
        // 「接口返回成功但库存没动」这种最难排查的不一致）
        expectCode(() -> inventoryCommandService.postPurchaseInbound(
                new PurchaseInventoryContract.InboundFact(
                        fx.order().getId(), fx.receipt().getId(), fx.receiptItemId(),
                        warehouseId, skuId, null, null, null, null,
                        DEFAULT_PURCHASE_UNIT, new BigDecimal("5.0000"), new BigDecimal("6.2000"),
                        "duplicate-probe", OffsetDateTime.now(), "W6 IT")), 41002);

        // DB 侧兜底：绕过应用层直接插同源流水 → 撞 uk_inventory_movement_source_active
        expectSqlFailure(
                "INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, source_document_type, "
                        + "source_document_id, source_document_item_id, quantity, unit_snapshot, "
                        + "before_quantity, after_quantity, occurred_at, deleted) "
                        + "VALUES (?, ?, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', ?, ?, 1, 'kg', 0, 1, "
                        + "CURRENT_TIMESTAMP, FALSE)",
                warehouseId, skuId, fx.receipt().getId(), fx.receiptItemId());

        // 余额没有被第二次入库改变
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // #12
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#12 Q13 单位一致累加：同 (wh,sku) 两次同单位入库 → 余额累加、unit 不变、回放链正确")
    void sameUnitInboundsAccumulateOnOneBalanceRow() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("IN12");
        W6Fixture first = inboundFixture("IN12a", skuId, "6.0000");
        W6Fixture second = inboundFixture("IN12b", skuId, "4.0000");

        confirmReceipt(first.receipt().getId(), "6.0000");
        confirmReceipt(second.receipt().getId(), "4.0000");

        // 一个 (warehouse, sku) 永远只有一行余额
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
        InventoryBalanceEntity balance = balanceRow(warehouseId, skuId);
        assertThat(balance.getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balance.getVersion()).isEqualTo(2);

        // 回放链：第二条的 before = 第一条的 after（append-only 账本的核心恒等式）
        List<Map<String, Object>> movements = movementsOf(warehouseId, skuId);
        assertThat(movements).hasSize(2);
        assertThat((BigDecimal) movements.get(0).get("before_quantity")).isEqualByComparingTo("0.0000");
        assertThat((BigDecimal) movements.get(0).get("after_quantity")).isEqualByComparingTo("6.0000");
        assertThat((BigDecimal) movements.get(1).get("before_quantity")).isEqualByComparingTo("6.0000");
        assertThat((BigDecimal) movements.get(1).get("after_quantity")).isEqualByComparingTo("10.0000");
        assertThat(movements.get(0).get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(movements.get(1).get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
    }

    // ------------------------------------------------------------------
    // #16
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#16 queryAvailability：无行 → 0/0（W6 起不再返回 null）；reserved 恒 0")
    void queryAvailabilityNeverReturnsNull() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("IN16");

        // 「确实没有库存」≠「没有库存能力」：返回 0 而不是 null
        PurchaseInventoryContract.Availability empty =
                inventoryCommandService.queryAvailability(skuId, warehouseId);
        assertThat(empty).isNotNull();
        assertThat(empty.available()).isEqualByComparingTo("0");
        assertThat(empty.reserved()).isEqualByComparingTo("0");

        // null 入参走宽松分支，同样不抛错、不返回 null
        assertThat(inventoryCommandService.queryAvailability(null, warehouseId)).isNotNull();
        assertThat(inventoryCommandService.queryAvailability(skuId, null)).isNotNull();

        W6Fixture fx = inboundFixture("IN16", skuId, "8.0000");
        confirmReceipt(fx.receipt().getId(), "8.0000");

        PurchaseInventoryContract.Availability filled =
                inventoryCommandService.queryAvailability(skuId, warehouseId);
        assertThat(filled.available()).isEqualByComparingTo("8.0000");
        // W6-1 没有占用机制：reserved 恒 0（OrderInventoryContract 保持零实现零调用）
        assertThat(filled.reserved()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------
    // #18
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#18 只读查询面：筛选 / 空态 / 详情 40486 / 拒绝客户端排序 / 流水溯源单号")
    void readOnlyQuerySurface() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("IN18");
        W6Fixture fx = inboundFixture("IN18", skuId, "7.0000");
        PurchaseReceiptVO confirmed = confirmReceipt(fx.receipt().getId(), "7.0000");

        // --- 余额：按 (warehouseId, skuId) 命中，展示字段实时联表 ---
        PageResult<InventoryBalanceVO> page =
                inventoryBalanceQueryService.query(balanceQuery(warehouseId, skuId));
        assertThat(page.getList()).hasSize(1);
        InventoryBalanceVO balance = page.getList().getFirst();
        assertThat(balance.getWarehouseCode()).isEqualTo(SEED_WAREHOUSE_CODE);
        assertThat(balance.getSkuCode()).isEqualTo(prefix + "IN18-K");
        assertThat(balance.getProductName()).isEqualTo(prefix + "IN18商品");
        assertThat(balance.getSpecValues()).isNotEmpty();
        assertThat(balance.getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(balance.getQuantity()).isEqualByComparingTo("7.0000");
        assertThat(balance.getVersion()).isEqualTo(1);

        // --- 余额：skuCode 模糊命中；不存在的编码 → 空态（「没有库存」不是错误）---
        InventoryBalanceQueryForm fuzzy = new InventoryBalanceQueryForm();
        fuzzy.setPageNum(1L);
        fuzzy.setPageSize(20L);
        fuzzy.setSkuCode(prefix + "IN18");
        assertThat(inventoryBalanceQueryService.query(fuzzy).getList()).hasSize(1);

        InventoryBalanceQueryForm noMatch = new InventoryBalanceQueryForm();
        noMatch.setPageNum(1L);
        noMatch.setPageSize(20L);
        noMatch.setSkuCode(prefix + "-NO-SUCH-SKU");
        assertThat(inventoryBalanceQueryService.query(noMatch).getList()).isEmpty();

        // --- 余额详情：命中 / 未知 id → 40486 ---
        assertThat(inventoryBalanceQueryService.detail(balance.getId()).getQuantity())
                .isEqualByComparingTo("7.0000");
        expectCode(() -> inventoryBalanceQueryService.detail(balance.getId() + 999_999_999L), 40486);

        // --- 拒绝客户端排序：join 查询里 `updated_at` 在四张表上都存在，
        //     静默忽略会让前端以为排序生效了，因此显式 40000 ---
        InventoryBalanceQueryForm sorted = balanceQuery(warehouseId, skuId);
        PageParam.SortItem sortItem = new PageParam.SortItem();
        sortItem.setColumn("updated_at");
        sortItem.setIsAsc(false);
        sorted.setSortItemList(List.of(sortItem));
        expectCode(() -> inventoryBalanceQueryService.query(sorted), 40000);

        // --- 流水：溯源字段（Q9 收货单号）+ 快照字段 ---
        PageResult<InventoryMovementVO> movementPage =
                inventoryMovementQueryService.query(movementQuery(warehouseId, skuId));
        assertThat(movementPage.getList()).hasSize(1);
        InventoryMovementVO movement = movementPage.getList().getFirst();
        assertThat(movement.getReceiptNo()).isEqualTo(confirmed.getReceiptNo());
        assertThat(movement.getSourceDocumentId()).isEqualTo(fx.receipt().getId());
        assertThat(movement.getSourceDocumentItemId()).isEqualTo(fx.receiptItemId());
        assertThat(movement.getMovementType()).isEqualTo("PURCHASE_IN");
        assertThat(movement.getSourceDocumentType())
                .isEqualTo(PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE);
        assertThat(movement.getUnitSnapshot()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(movement.getUnitCost()).isEqualByComparingTo("6.2000");
        assertThat(movement.getBeforeQuantity()).isEqualByComparingTo("0.0000");
        assertThat(movement.getAfterQuantity()).isEqualByComparingTo("7.0000");
        assertThat(movement.getOperator()).isEqualTo(receiptOperator(fx.receipt().getId()));

        // --- 流水时间范围：过滤的是 occurred_at（业务发生时刻），左闭右开 ---
        InventoryMovementQueryForm inWindow = movementQuery(warehouseId, skuId);
        inWindow.setOccurredFrom(movement.getOccurredAt().minusSeconds(1));
        inWindow.setOccurredTo(movement.getOccurredAt().plusSeconds(1));
        assertThat(inventoryMovementQueryService.query(inWindow).getList()).hasSize(1);

        InventoryMovementQueryForm outWindow = movementQuery(warehouseId, skuId);
        // occurredTo 取 occurredAt 本身 → `occurred_at < occurredTo` 为假 → 排除（左闭右开）
        outWindow.setOccurredTo(movement.getOccurredAt());
        assertThat(inventoryMovementQueryService.query(outWindow).getList()).isEmpty();

        // --- 类型白名单：枚举与 DB CHECK 同源（W6-1 = PURCHASE_IN；出库波次追加 SALES_OUT）---
        assertThat(ScmInventoryMovementTypeEnum.isSupported("PURCHASE_IN")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("SALES_OUT")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported(null)).isFalse();
        // 尚未落地的类型仍不得放行
        assertThat(ScmInventoryMovementTypeEnum.isSupported("TRANSFER_IN")).isFalse();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("STOCKTAKE_ADJUST")).isFalse();
    }
}
