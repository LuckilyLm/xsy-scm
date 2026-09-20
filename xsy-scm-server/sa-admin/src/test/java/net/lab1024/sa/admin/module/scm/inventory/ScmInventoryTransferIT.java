package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryTransferFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferAddForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferService;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.support.WarehouseDisableGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调拨的 PostgreSQL 集成测试（调拨波次）。
 *
 * <p>覆盖七件在单测里验证不了的事：
 * <ol>
 *   <li><b>两步式的中间态</b> —— 发出后源仓已扣、目标仓未加（在途期间这批货不在任何余额行里），
 *       这是本波次最核心的语义；</li>
 *   <li><b>一条明细行产生两条流水</b> —— 转出与转入各占一个来源类型，
 *       这是被迫的（{@code uk_inventory_movement_source_active} 只认
 *       {@code (source_document_type, source_document_item_id)}）；</li>
 *   <li><b>源仓的可用量门槛</b> —— 不得让源仓变负，也不得吃掉源仓已预留的货；</li>
 *   <li><b>两仓单位必须一致</b> —— Q13 不做隐式换算（41044）；</li>
 *   <li><b>成本随货平移</b> —— 转入腿的成本回读同一明细行的转出腿，目标仓自己的均价
 *       （新建行时是 0）不参与定价；</li>
 *   <li><b>状态机</b> —— 在途不可取消，两个终态不可回退；</li>
 *   <li><b>在途调拨阻塞仓库停用</b> —— 调拨波次新增的第四条停用阻塞条件。</li>
 * </ol>
 */
@DisplayName("调拨（PG IT）")
class ScmInventoryTransferIT extends ScmW6PgITBase {

    @Autowired
    private InventoryTransferService transferService;

    @Autowired
    private net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferQueryService transferQueryService;

    @Autowired
    private InventoryReservationService reservations;

    @Autowired
    private WarehouseDisableGuard warehouseDisableGuard;

    /** 造一个已入库指定数量到**默认启用仓库**的 SKU，返回 skuId。 */
    private Long stockedInSeed(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return skuId;
    }

    private static InventoryTransferAddForm.Item item(Long skuId, String quantity) {
        InventoryTransferAddForm.Item row = new InventoryTransferAddForm.Item();
        row.setSkuId(skuId);
        row.setQuantity(new BigDecimal(quantity));
        return row;
    }

    private InventoryTransferAddForm form(Long from, Long to, Long sku, String qty) {
        InventoryTransferAddForm form = new InventoryTransferAddForm();
        form.setFromWarehouseId(from);
        form.setToWarehouseId(to);
        form.setItems(new ArrayList<>(List.of(item(sku, qty))));
        return form;
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_transfer WHERE id = ?", String.class, id);
    }

    /** 指定类型的流水（同一 (仓库, SKU) 上可能同时有转出与转入）。 */
    private Map<String, Object> movementOf(Long wh, Long sku, String movementType) {
        return movementsOf(wh, sku).stream()
                .filter(m -> movementType.equals(String.valueOf(m.get("movement_type"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 " + movementType + " 流水"));
    }

    private static BigDecimal decimal(Map<String, Object> row, String column) {
        return new BigDecimal(String.valueOf(row.get(column)));
    }

    // ------------------------------------------------------------------
    // 两步式核心
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两步式：发出后源仓已扣、目标仓未加；收货后目标仓建立余额行并累加")
    void twoStepShipThenReceiveMovesStockAcrossWarehouses() {
        Long sku = stockedInSeed("tf1", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF1B");

        Long id = transferService.create(form(wh1, wh2, sku, "4.0000"));

        // 草稿：什么都没动
        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balanceRow(wh2, sku)).isNull();

        transferService.ship(id);

        // 发出后：源仓已扣、目标仓**还没有** —— 在途期间这批货不在任何余额行里。
        // 这不是缺陷，是两步式的必然结果（inventory_balance 只表达「在仓库里的货」）。
        assertThat(statusOf(id)).isEqualTo("SHIPPED");
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("6.0000");
        assertThat(balanceRow(wh2, sku)).isNull();

        Map<String, Object> out = movementOf(wh1, sku, "TRANSFER_OUT");
        assertThat(out.get("source_document_type")).isEqualTo("TRANSFER_OUT_ITEM");
        assertThat(decimal(out, "quantity")).isEqualByComparingTo("4.0000");
        assertThat(decimal(out, "before_quantity")).isEqualByComparingTo("10.0000");
        assertThat(decimal(out, "after_quantity")).isEqualByComparingTo("6.0000");
        // V34 起：调拨转出按**源仓当时的均价**记成本（出库不改变均价）。
        // 此前这里断言 `isNull()`，那是成本核算上线前的语义。
        // 这条流水同时是收货时的转入成本基准 —— 见 transferInCost 的回读。
        assertThat(decimal(out, "unit_cost"))
                .isEqualByComparingTo(balanceRow(wh1, sku).getAvgCost());

        transferService.receive(id);

        // 收货后：目标仓由本次调入**建立**余额行（入方向允许建行），源仓不再变化
        assertThat(statusOf(id)).isEqualTo("RECEIVED");
        assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("4.0000");
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("6.0000");

        Map<String, Object> in = movementOf(wh2, sku, "TRANSFER_IN");
        assertThat(in.get("source_document_type")).isEqualTo("TRANSFER_IN_ITEM");
        assertThat(decimal(in, "quantity")).isEqualByComparingTo("4.0000");
        assertThat(decimal(in, "before_quantity")).isEqualByComparingTo("0.0000");
        assertThat(decimal(in, "after_quantity")).isEqualByComparingTo("4.0000");
        // 单位快照 = 源仓记账单位（Q13），目标仓新建行时用的就是它
        assertThat(String.valueOf(in.get("unit_snapshot"))).isEqualTo(balanceRow(wh1, sku).getUnit());

        // **成本守恒**：转入腿带的是转出腿的成本，不是目标行当时的 0 均价。
        // 旧实现取目标行的 avg_cost（新建行 = 0），整批货的成本就此清零 —— 数量对、金额账全丢。
        assertThat(decimal(in, "unit_cost"))
                .as("转入腿回读同一明细行转出腿的成本")
                .isEqualByComparingTo(decimal(out, "unit_cost"));
        assertThat(balanceRow(wh2, sku).getAvgCost())
                .as("新建的目标余额行按转入成本入账")
                .isEqualByComparingTo(decimal(out, "unit_cost"));
    }

    @Test
    @DisplayName("一条明细行产生两条流水，两个方向各占一个来源类型（唯一索引的要求）")
    void oneItemProducesTwoMovementsUnderTwoSourceTypes() {
        Long sku = stockedInSeed("tf2", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF2B");

        Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
        transferService.ship(id);
        transferService.receive(id);

        // 同一明细行 id 被两条流水引用 —— 若共用一个来源类型，第二条会撞唯一索引
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM inventory_transfer_item WHERE transfer_id = ? AND deleted = FALSE",
                Long.class, id);
        // 必须带上来源类型：明细行 id 只在其来源表内唯一，共享开发库里其它单据表的明细行
        // 可能取到同一个数值。谓词与 uk_inventory_movement_source_active 的冲突域一致，
        // 断言语义不变 —— 两条腿若共用一个类型，这里只会拿到一行。
        List<Map<String, Object>> both = jdbc.queryForList(
                "SELECT movement_type, source_document_type FROM inventory_movement "
                        + "WHERE source_document_item_id = ? "
                        + "AND source_document_type IN ('TRANSFER_OUT_ITEM', 'TRANSFER_IN_ITEM') "
                        + "ORDER BY id", itemId);
        assertThat(both).hasSize(2);
        assertThat(both.get(0).get("movement_type")).isEqualTo("TRANSFER_OUT");
        assertThat(both.get(0).get("source_document_type")).isEqualTo("TRANSFER_OUT_ITEM");
        assertThat(both.get(1).get("movement_type")).isEqualTo("TRANSFER_IN");
        assertThat(both.get(1).get("source_document_type")).isEqualTo("TRANSFER_IN_ITEM");
    }

    // ------------------------------------------------------------------
    // 源仓的下限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("转出超过源仓现有量被拒（41043），单据仍是草稿、目标仓不变")
    void shipBeyondOnHandIsRejected() {
        Long sku = stockedInSeed("tf3", "5.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF3B");

        Long id = transferService.create(form(wh1, wh2, sku, "6.0000"));
        expectCode(() -> transferService.ship(id), 41043);

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(balanceRow(wh2, sku)).isNull();
    }

    @Test
    @DisplayName("转出不得吃掉源仓已预留的货（41043）—— 可用量 = 现有量 − 预留量")
    void shipCannotConsumeReservedStock() {
        Long sku = stockedInSeed("tf4", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF4B");

        reservations.reserve(new ReserveInventoryFact(
                wh1, sku, "SALES_ORDER_ITEM", 750001L, 850001L,
                new BigDecimal("8.0000"), OffsetDateTime.now(), null));

        // 现有量 10，其中 8 已预留 → 可用量只有 2
        Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
        expectCode(() -> transferService.ship(id), 41043);
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balanceRow(wh1, sku).getReservedQuantity()).isEqualByComparingTo("8.0000");

        // 出满可用量是允许的
        Long ok = transferService.create(form(wh1, wh2, sku, "2.0000"));
        transferService.ship(ok);
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("源仓从未入库该 SKU → 发出被拒（41046），且不会建出零余额行")
    void shipWithoutSourceBalanceIsRejectedWithoutCreatingRow() {
        Long skuId = newSkuOfType("tf5", "NON_STANDARD", "ON_SHELF");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF5B");
        assertThat(balanceRowCount(wh1, skuId)).isZero();

        Long id = transferService.create(form(wh1, wh2, skuId, "1.0000"));
        expectCode(() -> transferService.ship(id), 41046);
        // 转出是「出」方向：没有余额行 = 无货可调，不建行
        assertThat(balanceRowCount(wh1, skuId)).isZero();
    }

    // ------------------------------------------------------------------
    // 两仓单位
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两仓记账单位不一致 → 收货被拒（41044），不做隐式换算；单据停在在途")
    void unitMismatchBetweenWarehousesIsRejectedAtReceive() {
        Long sku = stockedInSeed("tf6", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF6B");

        // 目标仓已有该 SKU，但记账单位是「箱」（源仓是 kg）
        jdbc.update("INSERT INTO inventory_balance "
                + "(warehouse_id, sku_id, unit, quantity, reserved_quantity, version, deleted) "
                + "VALUES (?, ?, '箱', 5, 0, 0, FALSE)", wh2, sku);
        evictMybatisCache();

        Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
        transferService.ship(id);

        expectCode(() -> transferService.receive(id), 41044);

        // 源仓已扣（发出确实发生了），目标仓不变，单据停在在途 —— 需要人工处理：
        // 统一两仓采购单位后重试收货，或反向调拨回源仓。
        assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("7.0000");
        assertThat(statusOf(id)).isEqualTo("SHIPPED");
    }

    // ------------------------------------------------------------------
    // 入口校验与状态机
    // ------------------------------------------------------------------

    @Test
    @DisplayName("源仓与目标仓相同被拒（41042）—— 那不是调拨，是把货来回加减")
    void sameWarehouseIsRejected() {
        Long sku = stockedInSeed("tf7", "10.0000");
        Long wh1 = seedWarehouseId();
        expectCode(() -> transferService.create(form(wh1, wh1, sku, "1.0000")), 41042);
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("同一 SKU 在调拨单里出现两次被拒（41045）")
    void duplicateSkuIsRejected() {
        Long sku = stockedInSeed("tf8", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF8B");

        InventoryTransferAddForm form = form(wh1, wh2, sku, "1.0000");
        form.getItems().add(item(sku, "2.0000"));
        expectCode(() -> transferService.create(form), 41045);
    }

    @Test
    @DisplayName("状态机：草稿不可收货；在途不可取消/改/删/再发出；已收货是终态")
    void transferStatusMachineIsTwoStepAndTerminal() {
        Long sku = stockedInSeed("tf9", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF9B");
        Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));

        // 草稿不可收货
        expectCode(() -> transferService.receive(id), 41039);

        transferService.ship(id);
        // 在途：只能收货。不可取消（货已物理离开源仓）、不可改、不可删、不可再发出
        expectCode(() -> transferService.cancel(id), 41039);
        expectCode(() -> transferService.update(id, form(wh1, wh2, sku, "9.0000")), 41039);
        expectCode(() -> transferService.delete(id), 41039);
        expectCode(() -> transferService.ship(id), 41039);

        transferService.receive(id);
        // 终态
        expectCode(() -> transferService.receive(id), 41039);
        expectCode(() -> transferService.cancel(id), 41039);
        expectCode(() -> transferService.delete(id), 41039);

        assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("3.0000");
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("7.0000");
    }

    @Test
    @DisplayName("草稿取消不产生任何库存影响")
    void cancelledDraftHasNoStockEffect() {
        Long sku = stockedInSeed("tf10", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF10B");

        Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
        transferService.cancel(id);

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        expectCode(() -> transferService.ship(id), 41039);
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balanceRow(wh2, sku)).isNull();
    }

    // ------------------------------------------------------------------
    // 仓库停用守卫（调拨波次新增第四条）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("在途调拨阻塞仓库停用（41009）—— 目标仓侧；草稿不阻塞")
    void inTransitTransferBlocksDestinationWarehouseDisable() {
        Long sku = stockedInSeed("tf11", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF11B");

        // 目标仓此时既无余额、也无采购单与收货单 → 无阻塞
        assertThat(warehouseDisableGuard.disableBlocker(wh2)).isNull();

        Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
        // 草稿还没动过任何库存，不阻塞
        assertThat(warehouseDisableGuard.disableBlocker(wh2)).isNull();

        transferService.ship(id);
        // 在途：目标仓还欠着一批要入库的货 → 阻塞
        assertThat(warehouseDisableGuard.disableBlocker(wh2))
                .isEqualTo(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER);
    }

    @Test
    @DisplayName("在途调拨阻塞仓库停用（41009）—— 源仓侧：货已出去但账上还没落地")
    void inTransitTransferBlocksSourceWarehouseDisable() {
        Long sku = stockedInSeed("tf12", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF12B");

        // 把全部货发出去：源仓余额清零，因此「正库存」这条阻塞条件不再成立
        Long id = transferService.create(form(wh1, wh2, sku, "10.0000"));
        transferService.ship(id);
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("0.0000");

        // 还要排掉另外两条，否则先触发的是它们、断言会假红。它们都不是本用例要验证的东西：
        //   * 正库存：本仓还有**其它** SKU 的余额 —— 来源是前面 NOT_SUPPORTED 的用例
        //     （它们会把数据提交进库），属测试间的顺序耦合，不是产品行为；
        //   * 在途采购单 / 待入库收货单：夹具自己建的采购单与收货单留下的。
        // 三条语句都在测试事务内，用例结束即回滚，不影响真实数据。
        //
        // 注意必须**同时**清零 reserved_quantity：ck_inventory_balance_available 要求
        // reserved <= quantity，那些提交进库的行上带着预留量，只清 quantity 会直接违反约束。
        jdbc.update("UPDATE inventory_balance SET quantity = 0, reserved_quantity = 0 "
                + "WHERE warehouse_id = ? AND deleted = FALSE", wh1);
        jdbc.update("UPDATE purchase_order SET status = 'RECEIVED' "
                + "WHERE warehouse_id = ? AND status IN ('SUBMITTED', 'PARTIALLY_RECEIVED')", wh1);
        jdbc.update("UPDATE purchase_receipt SET putaway_status = 'COMPLETED' "
                + "WHERE warehouse_id = ? AND status = 'CONFIRMED' AND putaway_status = 'PENDING'", wh1);
        evictMybatisCache();

        // 另外三条都已排除，此时若仍返回 41009，它就只可能来自新增的第四条
        // —— 断言本身即是证明：若别的条件先命中，这里会拿到 41005 / 41006 / 41007。
        assertThat(warehouseDisableGuard.disableBlocker(wh1))
                .isEqualTo(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER);
    }

    // ------------------------------------------------------------------
    // 在途库存报表（「在途可见」的落地方式：报表增列，不进余额表）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("在途库存报表：只列已发出的调拨量，收货后消失，且不进余额表")
    void inTransitReportListsOnlyShippedTransfers() {
        Long sku = stockedInSeed("tf15", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF15B");

        Long id = transferService.create(form(wh1, wh2, sku, "4.0000"));

        // 草稿：还没动过库存，不在在途报表里
        assertThat(transferQueryService.queryInTransit())
                .as("草稿调拨不在在途报表里")
                .noneMatch(row -> sku.equals(row.getSkuId()));

        transferService.ship(id);

        // 在途：出现，数量是发出量，方向与单位都可读
        var row = transferQueryService.queryInTransit().stream()
                .filter(r -> sku.equals(r.getSkuId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("已发出的调拨应出现在在途报表里"));
        assertThat(row.getTransferNo()).isNotBlank();
        assertThat(row.getFromWarehouseId()).isEqualTo(wh1);
        assertThat(row.getToWarehouseId()).isEqualTo(wh2);
        assertThat(row.getQuantity()).isEqualByComparingTo("4.0000");
        assertThat(row.getUnit()).isEqualTo(balanceRow(wh1, sku).getUnit());

        // **核心断言**：在途量不在余额表里 —— 目标仓此时还没有余额行。
        // 这正是「不引入虚拟在途仓」的代价，也是这份报表存在的理由：
        // 对账时必须把它算进去，否则「全仓总库存」在在途期间会对不上。
        assertThat(balanceRow(wh2, sku)).as("在途期间目标仓没有余额行").isNull();

        transferService.receive(id);

        // 收货后从在途报表消失，同时目标仓出现余额
        assertThat(transferQueryService.queryInTransit())
                .as("收货后不再是在途")
                .noneMatch(r -> sku.equals(r.getSkuId()));
        assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("4.0000");
    }

    // ------------------------------------------------------------------
    // 命令侧契约
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一来源行重复转出被拒（41047），不会扣两次")
    void duplicateTransferOutFromSameSourceLineIsRejected() {
        Long sku = stockedInSeed("tf13", "10.0000");
        Long wh1 = seedWarehouseId();

        InventoryTransferFact fact = new InventoryTransferFact(
                wh1, sku, 0L, 940001L, new BigDecimal("2.0000"), null,
                OffsetDateTime.now(), "test:1");
        assertThat(inventoryCommandService.postTransferOut(fact)).isNotBlank();

        expectCode(() -> inventoryCommandService.postTransferOut(fact), 41047);
        assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("Q7：调拨流水同样不可改删（新增类型不是绕过 append-only 的口子）")
    void transferMovementsAreStillAppendOnly() {
        Long sku = stockedInSeed("tf14", "10.0000");
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("TF14B");

        Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
        transferService.ship(id);
        transferService.receive(id);

        for (Long movementId : List.of(
                ((Number) movementOf(wh1, sku, "TRANSFER_OUT").get("id")).longValue(),
                ((Number) movementOf(wh2, sku, "TRANSFER_IN").get("id")).longValue())) {
            expectSqlFailure("UPDATE inventory_movement SET deleted = TRUE WHERE id = ?", movementId);
            expectSqlFailure("DELETE FROM inventory_movement WHERE id = ?", movementId);
        }
    }
}
