package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferAddForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryConversionService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V37「零成本余额行重放修复」的 PostgreSQL 集成测试。
 *
 * <p><b>被测对象是迁移文件里的原文 SQL</b>，不是测试里另抄的一份（同 {@code ScmInventoryBackfillIT}
 * 的理由：抄一份会让「迁移已验证」变成一句没有依据的话）。
 *
 * <p>V37 修的是「调拨转入 / 转换转入把成本清零」留下的历史数据 —— 那两行余额数量正确、
 * {@code avg_cost} 为 0，成本事实只存在于配对的转出腿流水上。因此本测试的核心是
 * <b>先造出缺陷上线后的真实数据形状</b>：走完整业务链（收货 → 调拨 / 转换），
 * 再把余额行的均价改回 0（模拟修复前的代码写下的值），然后交给迁移重放。
 * 直接插一行假余额 + 假流水也能让迁移跑出数字，但那证明不了「重放口径」与
 * 「应用代码的记账口径」同源 —— 而这条同源性正是 V37 唯一值得测的地方。
 *
 * <p>另外三条判据（缺配对转出腿 / 账实不符必须响亮失败，盘盈不得凭空造价）走**直插流水**：
 * 流水是 append-only（V21 触发器），真实业务路径造不出这些形状，只有直接 INSERT 能。
 */
@DisplayName("V37 零成本余额重放修复（PG IT）")
class ScmInventoryRepriceMigrationIT extends ScmW6PgITBase {

    private static final String V37 = "V37__scm_inventory_reprice_zero_cost_balances.sql";

    @Autowired
    private InventoryTransferService transferService;

    @Autowired
    private InventoryConversionService conversionService;

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /**
     * 造一个已按默认采购价（6.2000）入库指定数量到**默认启用仓库**的 SKU。
     */
    private Long stockedInSeed(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = freeInboundFixture(suffix, skuId, quantity, "kg");
        confirmReceipt(fixture.receipt().getId(), quantity);
        return skuId;
    }

    private Long transferOneWay(Long from, Long to, Long skuId, String quantity) {
        InventoryTransferAddForm form = new InventoryTransferAddForm();
        form.setFromWarehouseId(from);
        form.setToWarehouseId(to);
        InventoryTransferAddForm.Item item = new InventoryTransferAddForm.Item();
        item.setSkuId(skuId);
        item.setQuantity(new BigDecimal(quantity));
        form.setItems(new ArrayList<>(List.of(item)));
        Long id = transferService.create(form);
        transferService.ship(id);
        transferService.receive(id);
        return id;
    }

    /**
     * 1 箱 → 10 kg 的整件拆零，审批完成。
     */
    private void convertOneWay(Long warehouseId, Long source, Long target,
                               String sourceQty, String targetQty) {
        InventoryConversionAddForm.Item item = new InventoryConversionAddForm.Item();
        item.setSourceSkuId(source);
        item.setSourceQuantity(new BigDecimal(sourceQty));
        item.setSourceUnit("箱");
        item.setTargetSkuId(target);
        item.setTargetQuantity(new BigDecimal(targetQty));
        item.setTargetUnit("kg");
        InventoryConversionAddForm form = new InventoryConversionAddForm();
        form.setWarehouseId(warehouseId);
        form.setConvertType("SPLIT");
        form.setItems(new ArrayList<>(List.of(item)));
        Long id = conversionService.create(form);
        InventoryConversionAuditForm audit = new InventoryConversionAuditForm();
        audit.setVersion(jdbc.queryForObject(
                "SELECT version FROM inventory_conversion WHERE id = ?", Integer.class, id));
        conversionService.approve(id, audit);
    }

    /**
     * 把库里**其它**零成本行暂时移出 V37 的候选集合。
     *
     * <p>V37 的候选是全库口径（{@code quantity > 0 AND avg_cost = 0}），而 IT 跑在共享开发库上，
     * 里面可能有与本次用例无关、本身账实就不平的零成本行 —— 那会让「V37 能不能修好我这行」
     * 变成「别人留下的破行能不能让我跑完」。只改均价一个字段，且全部在用例事务内，结束即回滚。
     */
    private void isolateCandidates(Collection<Long> keepSkuIds) {
        assertThat(keepSkuIds).as("候选集合必须留下至少一个本用例自己的行").isNotEmpty();
        String placeholders = keepSkuIds.stream().map(id -> "?").collect(Collectors.joining(","));
        // 拼进去的只有 "?" 占位符本身，SKU id 一律走绑定参数。
        jdbc.update("UPDATE inventory_balance SET avg_cost = 0.0001 "
                + "WHERE deleted = FALSE AND quantity > 0 AND avg_cost = 0 "
                + "AND sku_id NOT IN (" + placeholders + ")", keepSkuIds.toArray());
    }

    private void setAvgCost(Long warehouseId, Long skuId, String avgCost) {
        jdbc.update("UPDATE inventory_balance SET avg_cost = ? WHERE warehouse_id = ? AND sku_id = ? "
                + "AND deleted = FALSE", new BigDecimal(avgCost), warehouseId, skuId);
        evictMybatisCache();
    }

    /**
     * V37 Step 1：重放候选行的流水并回写 avg_cost。
     */
    private void runReprice() {
        jdbc.execute(migrationSection(V37, "-- Step 1", "-- Step 2"));
    }

    /**
     * V37 Step 2：收尾断言（候选集合内不得残留「账本有成本、余额零均价」的行）。
     */
    private void runRepriceAssertion() {
        String sql = migrationSql(V37);
        int from = sql.indexOf("-- Step 2");
        assertThat(from).as("%s 中找不到 %s（标记被改名了？）", V37, "-- Step 2")
                .isGreaterThanOrEqualTo(0);
        jdbc.execute(sql.substring(from));
    }

    private void runV37() {
        runReprice();
        runRepriceAssertion();
    }

    private BigDecimal avgCostOf(Long warehouseId, Long skuId) {
        return balanceRow(warehouseId, skuId).getAvgCost();
    }

    /**
     * 直插一条流水。
     *
     * <p>只有直插才能造出「转入腿找不到配对转出腿」这类**账本残缺**形状 ——
     * 流水是 append-only，业务路径既写不出它也删不掉它。
     */
    private void insertMovement(Long warehouseId, Long skuId, String movementType,
                                String sourceDocumentType, Long sourceItemId,
                                String quantity, String unitCost) {
        jdbc.update("INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, "
                        + "source_document_type, source_document_id, source_document_item_id, "
                        + "quantity, unit_snapshot, unit_cost, before_quantity, after_quantity, "
                        + "occurred_at, operator, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'kg', ?::numeric, 0, ?::numeric, "
                        + "CURRENT_TIMESTAMP, ?, FALSE)",
                warehouseId, skuId, movementType, sourceDocumentType, 999000L, sourceItemId,
                new BigDecimal(quantity), unitCost == null ? null : new BigDecimal(unitCost),
                new BigDecimal(quantity), "V37 IT");
    }

    private void insertZeroCostBalance(Long warehouseId, Long skuId, String quantity) {
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, sku_id, unit, quantity, "
                        + "reserved_quantity, avg_cost, version, deleted) "
                        + "VALUES (?, ?, 'kg', ?, 0, 0, 0, FALSE)", warehouseId, skuId,
                new BigDecimal(quantity));
        evictMybatisCache();
    }

    // ------------------------------------------------------------------
    // 重放修复
    // ------------------------------------------------------------------

    @Test
    @DisplayName("调拨转入被清零的历史余额行：按同一明细行转出腿的成本重放修复，且可重跑")
    void transferZeroedRowIsRepricedFromItsOutboundLeg() {
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("V37A");
        Long sku = stockedInSeed("v37a", "10.0000");
        transferOneWay(wh1, wh2, sku, "4.0000");

        // 修复后的代码本来就会写对均价；这里把它改回 0，模拟**缺陷上线期间**留下的数据形状。
        isolateCandidates(List.of(sku));
        setAvgCost(wh2, sku, "0.0000");

        runV37();

        assertThat(avgCostOf(wh2, sku)).as("取转出腿的 6.2000，而不是目标行自己的 0")
                .isEqualByComparingTo("6.2000");
        assertThat(balanceRow(wh2, sku).getQuantity()).as("数量本来就对，重放不该动它")
                .isEqualByComparingTo("4.0000");

        // 幂等：派生值由账本唯一决定，重跑必须零变化（迁移可能在任何一次启动里被重放评估）。
        runV37();
        assertThat(avgCostOf(wh2, sku)).isEqualByComparingTo("6.2000");

        // 修复过的行不再落回零均价：Step 2 的收尾断言也必须在重跑后依然成立。
        runRepriceAssertion();
    }

    @Test
    @DisplayName("转换转入被清零的历史余额行：按配对转出腿的总成本 ÷ 转入数量折算")
    void conversionZeroedRowIsRepricedFromItsPairedLeg() {
        Long wh = seedWarehouseId();
        Long source = newSkuOfType("v37b", "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = freeInboundFixture("v37b", source, "5.0000", "箱");
        confirmReceipt(fixture.receipt().getId(), "5.0000");
        Long target = newSkuOfType("v37bt", "NON_STANDARD", "ON_SHELF");
        convertOneWay(wh, source, target, "2.0000", "20.0000");

        isolateCandidates(List.of(source, target));
        setAvgCost(wh, target, "0.0000");

        runV37();

        // 2 箱 × 6.20 = 12.40 的总成本摊到 20 kg → 0.6200 / kg（守恒的是总额，不是单价）。
        assertThat(avgCostOf(wh, target)).isEqualByComparingTo("0.6200");
        assertThat(avgCostOf(wh, source)).as("源行的均价本来就没被转换改过").isEqualByComparingTo("6.2000");
    }

    @Test
    @DisplayName("修复范围只有零均价：非零但口径偏差的行不动（那是 V34 登记的已知近似）")
    void nonZeroRowsAreLeftAlone() {
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("V37C");
        Long sku = stockedInSeed("v37c", "10.0000");
        // 目标仓已有该 SKU、均价 1.0000（与源仓不同价）：调入后均价是被加权过的非零值。
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, sku_id, unit, quantity, "
                + "reserved_quantity, avg_cost, version, deleted) "
                + "VALUES (?, ?, 'kg', 10, 0, 1.0000, 0, FALSE)", wh2, sku);
        evictMybatisCache();
        transferOneWay(wh1, wh2, sku, "2.0000");

        BigDecimal afterTransfer = avgCostOf(wh2, sku);
        assertThat(afterTransfer).as("(10×1.00 + 2×6.20) / 12 = 1.8667").isEqualByComparingTo("1.8667");

        isolateCandidates(List.of(sku));
        runV37();

        assertThat(avgCostOf(wh2, sku)).as("非零均价不在 V37 的候选集合里，必须一字不动")
                .isEqualByComparingTo(afterTransfer);
    }

    // ------------------------------------------------------------------
    // 响亮失败
    // ------------------------------------------------------------------

    @Test
    @DisplayName("账实不符的候选行：重放数量对不上存量 → RAISE，不写一个看起来合理的均价")
    void brokenReplayFailsLoudly() {
        Long wh1 = seedWarehouseId();
        Long wh2 = newWarehouse("V37D");
        Long sku = stockedInSeed("v37d", "10.0000");
        transferOneWay(wh1, wh2, sku, "4.0000");

        isolateCandidates(List.of(sku));
        setAvgCost(wh2, sku, "0.0000");
        // 把存量数量改成流水加不出来的值：这行已经坏到不能用账本定价。
        jdbc.update("UPDATE inventory_balance SET quantity = quantity + 1 "
                + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE", wh2, sku);
        evictMybatisCache();

        assertThatThrownBy(this::runReprice)
                .hasMessageContaining("V37 cannot price")
                .hasMessageContaining("ledger replays to");
    }

    @Test
    @DisplayName("转入腿找不到配对转出腿 → RAISE：按 0 定价正是本迁移要消除的缺陷")
    void inboundLegWithoutPairedOutboundFailsLoudly() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("v37e", "NON_STANDARD", "ON_SHELF");
        isolateCandidates(List.of(sku));
        insertZeroCostBalance(wh, sku, "5.0000");
        // TRANSFER_IN 有了，但同一明细行的 TRANSFER_OUT 从没写过 —— 账本残缺。
        insertMovement(wh, sku, "TRANSFER_IN", "TRANSFER_IN_ITEM",
                sentinelItemId(), "5.0000", "0.0000");

        assertThatThrownBy(this::runReprice)
                .hasMessageContaining("V37 cannot price")
                .hasMessageContaining("no paired outbound movement");
    }

    @Test
    @DisplayName("盘盈进来的零成本行保持 0：迁移不凭空造价格")
    void stockGainOnlyRowIsNotInventedAPrice() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("v37f", "NON_STANDARD", "ON_SHELF");
        isolateCandidates(List.of(sku));
        insertZeroCostBalance(wh, sku, "5.0000");
        // STOCKTAKE_GAIN 的 unit_cost 为 NULL：盘点补的是数量，不是采购价格事实。
        insertMovement(wh, sku, "STOCKTAKE_GAIN", "STOCKTAKE_ITEM",
                sentinelItemId(), "5.0000", null);

        runV37();

        assertThat(avgCostOf(wh, sku)).as("没有成本事实就仍是 0，不编一个价")
                .isEqualByComparingTo("0.0000");
    }

    /**
     * 每次调用给一个没用过的来源行 id，避免撞 {@code uk_inventory_movement_source_active}。
     */
    private static Long sentinelItemId() {
        return ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L);
    }

    // ------------------------------------------------------------------
    // 契约
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V37 是纯派生状态修复：不碰流水、不碰余额数量，且只有两条 DO")
    void migrationRepairsOnlyDerivedState() {
        String sql = migrationSql(V37);

        assertThat(sql).as("流水是 append-only 账本，迁移一行都不许改")
                .doesNotContain("UPDATE inventory_movement")
                .doesNotContain("DELETE FROM inventory_movement");
        assertThat(sql).as("只回填 avg_cost 这一个派生列")
                .doesNotContain("SET quantity")
                .doesNotContain("SET reserved_quantity");
        assertThat(sql.split("DO \\$\\$", -1).length - 1).isEqualTo(2);
    }

    @Test
    @DisplayName("候选口径写在 SQL 里：quantity > 0 AND avg_cost = 0 两处都必须在")
    void candidateScopeIsTheZeroCostSymptom() {
        String step1 = migrationSection(V37, "-- Step 1", "-- Step 2");
        String step2 = migrationSql(V37).substring(migrationSql(V37).indexOf("-- Step 2"));

        assertThat(step1).contains("AND b.quantity > 0").contains("AND b.avg_cost = 0");
        assertThat(step2).contains("AND b.quantity > 0").contains("AND b.avg_cost = 0");
    }
}
