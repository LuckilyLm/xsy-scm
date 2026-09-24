package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发首建余额与并发收货（W6 Target Design §12.1 #5 / #6，§8.2 / §8.3 / §8.4）。
 *
 * <p><b>为什么必须关掉测试事务</b>：并发要真并发，就需要两个**互相独立的事务**；
 * 把两个线程塞进同一个测试事务里，它们会共享同一条连接、互相看不见对方，
 * 压不到任何锁与 {@code ON CONFLICT}。因此本类用 {@code Propagation.NOT_SUPPORTED}，
 * 让每次 Service 调用自己开事务（代价是造数提交到开发库，编码带随机前缀隔离）。
 *
 * <p><b>#5 压的是什么</b>：{@code InventoryCommandService} 的第 2、3 步 ——
 * {@code INSERT ... ON CONFLICT (warehouse_id, sku_id) WHERE deleted = FALSE DO NOTHING}
 * 之后 {@code SELECT ... FOR UPDATE}。为了让两个线程**同时**到达这一步，
 * 两张采购单必须毫无关联（不同供应商 / 客户 / 采购单 / 收货单）：
 * 否则它们会先在采购单行锁上串行化，余额首建的竞态根本不会发生。
 * 这也是 Q11 要求「在真实 PostgreSQL 上验证这条 SQL」的落点。
 *
 * <p><b>#6 压的是什么</b>：同一采购单的两张收货单并发确认。这里**期望串行化**：
 * 两个事务都要先拿采购单行锁，于是 {@code accumulateReceived} 不会丢失更新。
 * 若锁序被写错（例如先锁余额再锁采购单），两个方向相反的加锁顺序会形成环，
 * PostgreSQL 会抛死锁错误 —— 本用例会让它显形（而不是偶发地在生产上出现）。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("W6 并发入库（PG IT，无外层事务）")
class ScmInventoryConcurrencyIT extends ScmW6PgITBase {

    /**
     * 并发等待上限：锁等待远小于它，超过即为死锁或活锁（比默默挂死好）。
     */
    private static final long TIMEOUT_SECONDS = 60;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    /**
     * 子线程没有请求上下文，必须自己塞一个身份（{@code ScmOperator.current()} 依赖它）。
     *
     * <p>{@code administratorFlag=true} 与本类主线程身份一致：本用例测的是<b>锁序与并发账</b>，
     * 收货确认 / 出库确认在 P0 之后带上了数据范围守卫，若这里给一个非超管且没有授权仓行的身份，
     * 两个线程会双双被 {@code ScmDataScopeException} 挡在业务逻辑之前 —— 测到的只是「都被拒」，
     * 而不是「只有一个抢到余额行」。范围本身由 {@code ScmInventoryWriteScopePgIT} 等专用例取证。
     */
    private static void setThreadOperator() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W6 concurrent IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record Outcome(boolean success, Throwable error) {
        static Outcome ok() {
            return new Outcome(true, null);
        }

        static Outcome failed(Throwable error) {
            return new Outcome(false, error);
        }
    }

    /**
     * 让两段动作尽可能同时开始，各自在**独立事务**里执行。
     *
     * <p>用 {@code CountDownLatch} 对齐起跑线而不是「先提交第一个再提交第二个」：
     * 后者只能测到串行路径，测不到竞态。
     */
    private List<Outcome> runConcurrently(ThrowingRunnable first, ThrowingRunnable second)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(
                    pool.submit(() -> {
                        setThreadOperator();
                        start.await();
                        try {
                            first.run();
                        } finally {
                            SmartRequestUtil.remove();
                        }
                        return null;
                    }),
                    pool.submit(() -> {
                        setThreadOperator();
                        start.await();
                        try {
                            second.run();
                        } finally {
                            SmartRequestUtil.remove();
                        }
                        return null;
                    }));
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>(2);
            for (Future<Object> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    outcomes.add(Outcome.ok());
                } catch (ExecutionException e) {
                    outcomes.add(Outcome.failed(e.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 按 **id（= 插入顺序 = 余额行锁的获取顺序）** 读流水。
     *
     * <p><b>为什么不按 {@code occurred_at}</b>：{@code receipt.confirmed_at} 是在**进入
     * 余额加锁之前**取的（confirm 第 335 行），因此并发下「确认时刻的先后」与
     * 「余额锁的先后」可以不一致。账本的 before/after 链是按**锁顺序**串起来的，
     * 所以并发用例必须按 id 排序才能看到正确的链；按 occurred_at 排序会看到
     * 一个顺序与数值不对应的序列 —— 那不是缺陷，是「并发事务的时间戳先于加锁」的必然结果。
     */
    private List<Map<String, Object>> movementsInLockOrder(Long warehouseId, Long skuId) {
        return jdbc.queryForList(
                "SELECT * FROM inventory_movement "
                        + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE ORDER BY id",
                warehouseId, skuId);
    }

    // ------------------------------------------------------------------
    // #5
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#5 并发首建余额：2 线程同 (wh,sku) 同时入库 → 1 行余额、两次增量都在、无异常逃逸")
    void concurrentFirstInboundCreatesExactlyOneBalanceRow() throws Exception {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("CC5");
        // 两个**互不相干**的采购单：采购侧零争用，两个线程才能同时到达「首建余额」
        W6Fixture first = inboundFixture("CC5a", skuId, "6.0000");
        W6Fixture second = inboundFixture("CC5b", skuId, "4.0000");
        assertThat(balanceRow(warehouseId, skuId)).as("前置：余额还不存在").isNull();

        List<Outcome> outcomes = runConcurrently(
                () -> confirmReceipt(first.receipt().getId(), "6.0000", prefix + ":CC5:a"),
                () -> confirmReceipt(second.receipt().getId(), "4.0000", prefix + ":CC5:b"));

        // 无异常逃逸：ON CONFLICT 的赢家与输家都必须平安落地
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.success())
                .as("并发入库不应有异常逃逸：%s", outcome.error())
                .isTrue());

        // 物理行数（含 soft-deleted）：排除「插了两行再删一行」这种伪唯一
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");

        // 两次增量都在（没有丢失更新），且每行都满足 after = before + quantity
        List<Map<String, Object>> movements = movementsInLockOrder(warehouseId, skuId);
        assertThat(movements).hasSize(2);
        movements.forEach(row -> assertThat((BigDecimal) row.get("after_quantity"))
                .isEqualByComparingTo(((BigDecimal) row.get("before_quantity"))
                        .add((BigDecimal) row.get("quantity"))));
        // 锁顺序下的链：0 → x → 10
        assertThat((BigDecimal) movements.get(0).get("before_quantity")).isEqualByComparingTo("0.0000");
        assertThat((BigDecimal) movements.get(1).get("after_quantity")).isEqualByComparingTo("10.0000");
        assertThat((BigDecimal) movements.get(1).get("before_quantity"))
                .isEqualByComparingTo((BigDecimal) movements.get(0).get("after_quantity"));
        // 两笔数量各就各位（谁先拿到锁不影响总量）
        assertThat(movements)
                .extracting(row -> ((BigDecimal) row.get("quantity")).toPlainString())
                .containsExactlyInAnyOrder("6.0000", "4.0000");

        // 两张收货单都确认成功
        assertThat(reloadReceipt(first.receipt().getId()).getStatus()).isEqualTo("CONFIRMED");
        assertThat(reloadReceipt(second.receipt().getId()).getStatus()).isEqualTo("CONFIRMED");
        assertThat(movementsOfReceiptItem(first.receiptItemId())).isEqualTo(1);
        assertThat(movementsOfReceiptItem(second.receiptItemId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // #6
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#6 并发 confirm 同一采购单的两张收货单：串行化、累计与库存一致、无死锁")
    void concurrentConfirmsOnSameOrderSerializeWithoutDeadlock() throws Exception {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("CC6");
        W6Fixture fixture = inboundFixture("CC6", skuId, "10.0000");
        // 分次到货：同一采购单的第二张收货单
        PurchaseReceiptVO second = createAnotherReceipt(fixture.order().getId(), "second");

        List<Outcome> outcomes = runConcurrently(
                () -> confirmReceipt(fixture.receipt().getId(), "5.0000", prefix + ":CC6:a"),
                () -> confirmReceipt(second.getId(), "5.0000", prefix + ":CC6:b"));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.success())
                .as("同单并发收货应当串行化成功，而不是死锁/版本冲突：%s", outcome.error())
                .isTrue());

        // 采购侧：累计 10（两个事务都先拿采购单行锁 → accumulateReceived 不丢更新）
        assertThat(receivedQuantityOf(fixture.orderItemId())).isEqualByComparingTo("10.0000");
        assertThat(reloadOrder(fixture.order().getId()).getStatus()).isEqualTo("RECEIVED");

        // 库存侧：1 行余额 = 10，2 条流水，每行恒等式成立
        assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
        List<Map<String, Object>> movements = movementsInLockOrder(warehouseId, skuId);
        assertThat(movements).hasSize(2);
        movements.forEach(row -> assertThat((BigDecimal) row.get("after_quantity"))
                .isEqualByComparingTo(((BigDecimal) row.get("before_quantity"))
                        .add((BigDecimal) row.get("quantity"))));
        assertThat((BigDecimal) movements.get(0).get("before_quantity")).isEqualByComparingTo("0.0000");
        assertThat((BigDecimal) movements.get(1).get("after_quantity")).isEqualByComparingTo("10.0000");
        // 两张收货单各自的收货行都恰好入库一次
        assertThat(movementsOfReceiptItem(fixture.receiptItemId())).isEqualTo(1);
        assertThat(movementsOfReceiptItem(second.getItems().getFirst().getId())).isEqualTo(1);
    }
}
