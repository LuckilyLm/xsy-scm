package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入库失败时的**真实回滚**（W6 Target Design §12.1 #3 / #13）。
 *
 * <p><b>为什么这个类必须关掉测试事务</b>：W1–W5 的 IT 基类把整个用例包在一个事务里，
 * 于是 Service 上 {@code @Transactional(rollbackFor = Exception.class)} 只是**加入**这个事务；
 * 抛异常时 Spring 只是把事务标记成 rollback-only，**不会**把已经写下的行撤掉。
 * 在这种环境下断言「失败后零残留」，看到的其实是「失败前写下的行还在」——
 * 断言会因为「检查发生在任何写入之前」而**碰巧通过**，但完全没有验证到原子性。
 *
 * <p>本类用 {@code Propagation.NOT_SUPPORTED} 关掉外层事务，让每次 Service 调用
 * 自己开事务、自己提交或回滚。于是每个断言都是**独立事务里的已提交读**，
 * 「采购侧全部写入 + 库存写入」要么整体在库里，要么整体不在。
 *
 * <p><b>代价</b>：造数会提交到开发库（与 {@code PricingIT} 的并发用例同一取舍）。
 * 所有编码都带 {@link #prefix} 的随机后缀，不会与其它用例或既有数据冲突。
 *
 * <p><b>缓存</b>：没有外层事务时每次 DAO 调用都是新的 SqlSession，
 * MyBatis 一级缓存天然为空，因此 {@link #evictMybatisCache()} 被覆盖成空实现 ——
 * 既没必要，也能避免在无事务上下文里反复开 session 而不关闭。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("W6 入库失败的真实回滚（PG IT，无外层事务）")
class ScmInventoryRollbackIT extends ScmW6PgITBase {

    @Override
    protected void evictMybatisCache() {
        // 见类注释：无外层事务 → 每次调用都是新 session → 一级缓存天然为空
    }

    // ------------------------------------------------------------------
    // #3
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#3 超收 40989：真实回滚后余额与流水零残留，采购侧与幂等记录也不动")
    void overReceiptLeavesNoResidue() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("RB3");
        W6Fixture fx = inboundFixture("RB3", skuId, "10.0000");

        // 可收上限 = planned × (1 + 容差 10%) = 11 → 20 必然超收
        expectCode(() -> confirmReceipt(fx.receipt().getId(), "20.0000"), 40989);

        // 库存侧：余额行根本没被创建，流水一条都没有
        assertThat(balanceRow(warehouseId, skuId)).isNull();
        assertThat(movementCount(warehouseId, skuId)).isZero();

        // 采购侧：收货单仍是草稿、采购行累计为 0、采购单状态没动
        assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("DRAFT");
        assertThat(receivedQuantityOf(fx.orderItemId())).isEqualByComparingTo("0.0000");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("SUBMITTED");

        // 幂等 claim 也随事务回滚：同一个键 + 同样的内容重试，**再次执行**并再次得到 40989。
        // 若 claim 残留成「已提交但缺 result_data」，这里会得到另一个错误码 —— 断言会立刻失败。
        String key = prefix + ":RB3:retry";
        expectCode(() -> confirmReceipt(fx.receipt().getId(), "20.0000", key), 40989);
        expectCode(() -> confirmReceipt(fx.receipt().getId(), "20.0000", key), 40989);

        // 换成合规数量后仍然能正常入库（失败的原因是超收，不是别的东西坏了）
        assertThat(confirmReceipt(fx.receipt().getId(), "10.0000").getStatus()).isEqualTo("CONFIRMED");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // #13
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#13 Q13 单位不匹配 41001：confirm 整体回滚（库存侧 + 采购侧零残留）")
    void unitMismatchRollsBackTheWholeConfirm() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("RB13");
        // 同一个 SKU 经两个供应商入库、采购单位不同 —— 只有「无需求来源」的采购行能构造出来
        // （有需求来源时 W5 Q17 要求采购单位 == 销售单位，异单位在分配阶段就被拒了）
        W6Fixture kilogram = freeInboundFixture("RB13a", skuId, "5.0000", "kg");
        W6Fixture box = freeInboundFixture("RB13b", skuId, "3.0000", "box");

        // 第一笔建立记账单位 kg
        confirmReceipt(kilogram.receipt().getId(), "5.0000");
        assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

        // 第二笔异单位 → 41001（显式失败，**不是**把 5kg + 3箱 加成一个没有物理意义的数）
        expectCode(() -> confirmReceipt(box.receipt().getId(), "3.0000"), 41001);

        // 库存侧：既没有第二行余额，也没有「加了一半」的余额，流水也没多
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

        // 采购侧：整笔确认被回滚 —— 这是「收货确认与入库同事务」最直接的证据。
        // 如果库存写入另开了事务（REQUIRES_NEW），这里会看到 CONFIRMED + 累计 3.0000。
        assertThat(reloadReceipt(box.receipt().getId()).getStatus()).isEqualTo("DRAFT");
        assertThat(receivedQuantityOf(box.orderItemId())).isEqualByComparingTo("0.0000");
        assertThat(reloadOrder(box.order().getId()).getStatus()).isEqualTo("SUBMITTED");

        // 换成同单位后仍可入库（被拒的原因是单位不一致，不是这张单坏了）
        W6Fixture kilogram2 = freeInboundFixture("RB13c", skuId, "2.0000", "kg");
        confirmReceipt(kilogram2.receipt().getId(), "2.0000");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("7.0000");
        assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
    }
}
