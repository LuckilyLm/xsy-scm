package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Q5 bootstrap backfill（W6 Target Design §12.1 #8 / #9 / #10 / #14 / #15）。
 *
 * <p><b>被测对象是 V19 里的原文 SQL</b>，不是测试里另抄的一份：基类
 * {@link ScmW6PgITBase#migrationSection} 从 classpath 上读迁移文件、按 {@code -- Step N}
 * 标记切段执行。抄一份 SQL 到测试里，抄错或漂移都会让「backfill 已验证」变成一句没有依据的话。
 *
 * <p>backfill 的每一条 INSERT 都以 {@code ON CONFLICT ... DO NOTHING} 收尾，
 * 所以**在测试事务里重放它是安全的**：语义与「迁移执行时」完全一致（幂等跳过已入库的源事实），
 * 而所有写入都在用例结束时回滚。
 *
 * <p>需要「历史数据」的场景用两个动作构造：
 * <ol>
 *   <li>{@code overrideReceiptConfirmedAt} / {@code overrideReceiptItemUnit} —— 把收货事实改成
 *       实时路径**永远产生不出来**的形状（乱序的确认时刻、混单位的采购快照）；</li>
 *   <li>{@code eraseMovementsFor} —— 把时钟拨回去：删掉实时路径已经写下的流水，
 *       让这些收货看起来「还没被任何路径入库过」，从而能观察 backfill 的回放结果。</li>
 * </ol>
 * 这两个动作都是**测试构造**，不是产品能力（产品侧没有任何删除/改写流水的入口）。
 */
@DisplayName("W6 backfill 回放与对账（PG IT）")
class ScmInventoryBackfillIT extends ScmW6PgITBase {

    /**
     * 本 SKU 的已确认收货行里，没有对应流水的条数（V19 Step 4 第一条判据的范围内重算）。
     *
     * <p>刻意按 SKU 收口而不是全库：V22 之后「已确认收货但尚未上架」是合法中间态，
     * 那条判据在全库上不再成立，但它对 backfill 负责回放的那批 DIRECT 收货事实仍然成立。
     */
    private int confirmedLineWithoutMovementCount(Long skuId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM purchase_receipt_item ri "
                        + "JOIN purchase_receipt r ON r.id = ri.purchase_receipt_id AND r.deleted = FALSE "
                        + "WHERE r.status = 'CONFIRMED' AND ri.deleted = FALSE AND ri.received_quantity > 0 "
                        + "  AND ri.sku_id = ? "
                        + "  AND NOT EXISTS (SELECT 1 FROM inventory_movement m "
                        + "                  WHERE m.source_document_type = 'PURCHASE_RECEIPT_ITEM' "
                        + "                    AND m.source_document_item_id = ri.id AND m.deleted = FALSE)",
                Integer.class, skuId);
    }

    // ------------------------------------------------------------------
    // #8
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#8 backfill 幂等与对账：重复执行零增量，Σ流水 = Σ收货、Σ余额 = Σ流水")
    void backfillIsIdempotentAndReconciles() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("BF8");
        W6Fixture first = inboundFixture("BF8a", skuId, "6.0000");
        W6Fixture second = inboundFixture("BF8b", skuId, "4.0000");
        confirmReceipt(first.receipt().getId(), "6.0000");
        confirmReceipt(second.receipt().getId(), "4.0000");

        assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");

        // 重放三段原文：前置检查 / 流水 / 余额（Step 4 的对账判据见 assertLedgerBalanced）
        runBackfillUnitPreCheck();
        runBackfillMovements();
        runBackfillBalances();

        // 零增量：没有第二条余额行、没有重复流水、数量没有被加第二次
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
        assertThat(movementsOfReceiptItem(first.receiptItemId())).isEqualTo(1);
        assertThat(movementsOfReceiptItem(second.receiptItemId())).isEqualTo(1);

        // 对账恒等式（独立于 V19 的 SQL 重算一遍，避免「迁移自己的断言自己说了算」）
        assertLedgerBalanced(warehouseId, skuId);
        assertThat(confirmedLineWithoutMovementCount(skuId)).isZero();

        // 本 (warehouse, sku) 的流水合计 = 收货合计
        assertThat(jdbc.queryForObject(
                "SELECT sum(quantity) FROM inventory_movement "
                        + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE",
                BigDecimal.class, warehouseId, skuId)).isEqualByComparingTo("10.0000");
    }

    // ------------------------------------------------------------------
    // #9
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#9 backfill 与实时路径不交叠：backfill 后新 confirm 正常入库，重跑 backfill 零增量")
    void backfillAndRealtimeDoNotOverlap() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("BF9");
        W6Fixture historical = inboundFixture("BF9a", skuId, "3.0000");
        confirmReceipt(historical.receipt().getId(), "3.0000");

        // 迁移跑过一遍之后，历史收货已经在账本里
        runBackfillMovements();
        runBackfillBalances();
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("3.0000");

        // 新 confirm 走实时路径（应用已启动），与 backfill 不冲突
        W6Fixture live = inboundFixture("BF9b", skuId, "2.0000");
        confirmReceipt(live.receipt().getId(), "2.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");

        // 再跑一遍 backfill：新 confirm 的源事实已入库 → 零增量（三条路径互不重复入库）
        runBackfillMovements();
        runBackfillBalances();
        assertLedgerBalanced(warehouseId, skuId);
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(movementsOfReceiptItem(live.receiptItemId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // #10
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#10 回放顺序（Q5 修正）：id 顺序 ≠ confirmed_at 顺序时，按 confirmed_at 回放")
    void backfillReplaysInConfirmedAtOrder() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("BF10");
        // 先建 A 后建 B → item id 一定 A < B
        W6Fixture earlierId = inboundFixture("BF10a", skuId, "6.0000");
        W6Fixture laterId = inboundFixture("BF10b", skuId, "4.0000");
        assertThat(earlierId.receiptItemId()).isLessThan(laterId.receiptItemId());

        // 但**先确认 B**（item id 大的那张），再确认 A —— 于是 id 顺序与 confirmed_at 顺序相反。
        // 这正是 Q5 要求「禁止仅 ORDER BY purchase_receipt_item.id」的原因。
        confirmReceipt(laterId.receipt().getId(), "4.0000");
        confirmReceipt(earlierId.receipt().getId(), "6.0000");

        // 固定成确定的历史时刻：B 在前、A 在后（相差 1 小时，顺序不可能被精度抹平）
        OffsetDateTime base = OffsetDateTime.parse("2026-01-01T00:00:00+08:00");
        overrideReceiptConfirmedAt(laterId.receipt().getId(), base);
        overrideReceiptConfirmedAt(earlierId.receipt().getId(), base.plusHours(1));

        // 把时钟拨回去：删掉实时路径写的流水，让 backfill 重新回放这段历史
        eraseMovementsFor(warehouseId, skuId);
        runBackfillMovements();

        List<Map<String, Object>> replayed = movementsOf(warehouseId, skuId);
        assertThat(replayed).hasSize(2);

        // 第一条必须是 confirmed_at 最早的那笔（B / 4.0000），before 从 0 起
        Map<String, Object> head = replayed.get(0);
        assertThat(head.get("source_document_item_id")).isEqualTo(laterId.receiptItemId());
        assertThat((BigDecimal) head.get("quantity")).isEqualByComparingTo("4.0000");
        assertThat((BigDecimal) head.get("before_quantity")).isEqualByComparingTo("0.0000");
        assertThat((BigDecimal) head.get("after_quantity")).isEqualByComparingTo("4.0000");

        // 第二条接着第一条（A / 6.0000），before = 4.0000
        Map<String, Object> tail = replayed.get(1);
        assertThat(tail.get("source_document_item_id")).isEqualTo(earlierId.receiptItemId());
        assertThat((BigDecimal) tail.get("quantity")).isEqualByComparingTo("6.0000");
        assertThat((BigDecimal) tail.get("before_quantity")).isEqualByComparingTo("4.0000");
        assertThat((BigDecimal) tail.get("after_quantity")).isEqualByComparingTo("10.0000");

        // 末条 after = 余额；若按 item id 回放，首条会是 6.0000 而 before 仍是 0 → 上面会失败
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
        assertLedgerBalanced(warehouseId, skuId);
    }

    // ------------------------------------------------------------------
    // #14
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#14 Q13 backfill 前置检查：同 (wh,sku) 混单位 → 迁移段 RAISE EXCEPTION，不静默汇总")
    void backfillRefusesToAggregateMixedUnits() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("BF14");
        // 两笔都按 kg 入库（实时路径允许，因为单位一致）
        W6Fixture a = freeInboundFixture("BF14a", skuId, "5.0000", "kg");
        W6Fixture b = freeInboundFixture("BF14b", skuId, "3.0000", "kg");
        confirmReceipt(a.receipt().getId(), "5.0000");
        confirmReceipt(b.receipt().getId(), "3.0000");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");

        // 单位一致时前置检查必须通过（否则它就成了一个永远失败的检查）
        runBackfillUnitPreCheck();

        // 人为制造「实时路径上线前遗留的脏数据」：同一 (wh, sku) 的历史收货行单位不一致。
        // 实时路径**永远产生不出**这种数据（异单位会被 41001 挡下），
        // 所以这正是前置检查存在的理由 —— 迁移要处理的是 W6 上线之前就躺在库里的历史。
        overrideReceiptItemUnit(b.receiptItemId(), "box");
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT ri.purchase_unit_snapshot) FROM purchase_receipt_item ri "
                        + "JOIN purchase_receipt r ON r.id = ri.purchase_receipt_id AND r.deleted = FALSE "
                        + "WHERE r.status = 'CONFIRMED' AND ri.deleted = FALSE AND ri.sku_id = ?",
                Integer.class, skuId)).isEqualTo(2);

        // 前置检查必须让迁移**失败**，而不是把 5kg + 3箱 汇总成 8
        expectSqlFailure(migrationSection(V19, "-- Step 1", "-- Step 2"));

        // 失败之后什么都没被改动（检查发生在任何写入之前）
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);

        // 把脏数据改回一致后，前置检查恢复通过（证明失败的原因就是混单位）
        overrideReceiptItemUnit(b.receiptItemId(), "kg");
        runBackfillUnitPreCheck();
    }

    // ------------------------------------------------------------------
    // #15
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#15 occurred_at / operator 取收货确认事实：实时与 backfill 同口径，不用 now() / ambient")
    void movementFactsComeFromTheReceiptConfirmation() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("BF15");
        W6Fixture fx = inboundFixture("BF15", skuId, "5.0000");
        confirmReceipt(fx.receipt().getId(), "5.0000");

        OffsetDateTime confirmedAt = receiptConfirmedAt(fx.receipt().getId());
        String operator = receiptOperator(fx.receipt().getId());
        assertThat(confirmedAt).isNotNull();
        // ScmOperator.current() = userType:userId → 基类把请求身份设成 (ADMIN_EMPLOYEE=1, employeeId=1)
        assertThat(operator).isEqualTo("1:1");

        Map<String, Object> live = movementsOf(warehouseId, skuId).getFirst();
        // **精确相等**，不是「大约相同」：流水的 occurred_at 就是收货确认时刻本身
        assertThat(timestampOf(live.get("occurred_at")).toInstant())
                .isEqualTo(confirmedAt.toInstant());
        assertThat(live.get("operator")).isEqualTo(operator);
        assertThat(live.get("created_by")).isEqualTo(operator);

        // --- 判别性验证：如果库存侧用 now() / ambient operator，下面两条一定会失败 ---
        // 把收货确认事实改成一个历史时刻 + 一个哨兵操作者，再让 backfill 回放
        OffsetDateTime historical = OffsetDateTime.parse("2020-03-15T08:30:00+08:00");
        overrideReceiptConfirmedAt(fx.receipt().getId(), historical);
        jdbc.update("UPDATE purchase_receipt SET operator = ? WHERE id = ?",
                "sentinel-operator", fx.receipt().getId());
        eraseMovementsFor(warehouseId, skuId);
        runBackfillMovements();

        Map<String, Object> replayed = movementsOf(warehouseId, skuId).getFirst();
        assertThat(timestampOf(replayed.get("occurred_at")).toInstant())
                .as("回放的 occurred_at 必须等于收货确认时刻（2020-03-15），而不是迁移执行时刻")
                .isEqualTo(historical.toInstant());
        assertThat(replayed.get("operator"))
                .as("回放的 operator 必须等于收货单的操作者，而不是环境里的当前操作者")
                .isEqualTo("sentinel-operator");
    }

    // ------------------------------------------------------------------
    // 对账判据的判别性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("对账判据可判别：绕过流水改账面必须报错，查不存在的 (wh,sku) 不许静默通过")
    void reconciliationJudgeDetectsTamperingAndRefusesVacuousPass() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("JUDGE");
        W6Fixture fx = inboundFixture("JUDGE", skuId, "6.0000");
        confirmReceipt(fx.receipt().getId(), "6.0000");
        assertLedgerBalanced(warehouseId, skuId);

        // 产品侧没有任何「改账面却不写流水」的入口，所以这条违规只能手工造
        jdbc.update("UPDATE inventory_balance SET quantity = quantity + 1 "
                        + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE",
                warehouseId, skuId);
        assertThatThrownBy(() -> assertLedgerBalanced(warehouseId, skuId))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("方向净额不一致");

        // 传错 id 时流水与余额两个聚合都是 NULL，差额判据本身无从判别
        assertThatThrownBy(() -> assertLedgerBalanced(warehouseId, -1L))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("没有活动余额行");
    }
}
