package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * 库存预留的**真并发**验证（主线计划 P0「补库存预留并发测试」）。
 *
 * <p>单线程 IT（{@code ScmInventoryOutboundIT}）只能证明「规则写对了」，证明不了
 * 「规则在竞态下仍然成立」。预留的三条硬要求都是竞态命题：
 * 不超卖、同一来源行不双计、释放不重复回补 —— 它们依赖
 * 「{@code lockByWarehouseAndSku} 的行锁一直持有到事务结束」这一件事，
 * 只有多个独立事务同时竞争同一余额行时才压得到。
 *
 * <p><b>为什么必须 {@code Propagation.NOT_SUPPORTED}</b>：把多个线程塞进同一个测试事务，
 * 它们会共享一条连接、互相看不见对方的未提交数据，锁与 {@code ON CONFLICT} 都不会发生。
 * 代价是造数会提交到目标库，因此全部标识带 {@link #prefix} 随机前缀隔离，
 * 来源行 id 也从 {@link #sourceIdSeed()} 派生而不用常量（常量会在第二轮运行撞
 * {@code uk_inventory_reservation_source_active}，把「防重生效」误报成红）。
 *
 * <p><b>期望的不是「都成功」</b>：这类竞态里失败是**正确行为**。因此每个用例都断言
 * 「成功数 + 失败码 + 最终账」三者一致，而不是只断言不抛异常 —— 后者会让
 * 「两个都失败」这种真故障（锁序写错导致死锁）悄悄通过。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("库存预留并发（PG IT，无外层事务）")
class ScmInventoryReservationConcurrencyIT extends ScmW6PgITBase {

    /**
     * 锁等待远小于它；超过即为死锁或活锁 —— 比默默挂死好。
     */
    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private InventoryReservationService reservations;

    /**
     * 出库命令与预留一样要求**调用方持有事务**（{@code InventoryCommandService} 会直接抛错），
     * 本类没有外层事务，因此跨到出库时必须显式开一个，而不是让 IllegalTransactionStateException
     * 冒充「可用量门槛拒绝」——那会让并发断言以错误的理由通过。
     */
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private void postSalesOutbound(InventoryOutboundFact fact) {
        transactionTemplate.executeWithoutResult(status -> inventoryCommandService.postSalesOutbound(fact));
    }

    /**
     * 造来源行号用的种子：必须**只增不减**，且落在库里已用行号之上。
     *
     * <p>原先写 {@code MAX(id) + 100000}，但预留表被别的用例部分清理过，{@code MAX(id)} 会回落：
     * 实测遗留行号已到 100778，而下一颗种子只算到 100740 —— 于是新一轮造出的行号与上一轮遗留的
     * ACTIVE 行重合，{@code uk_inventory_reservation_source_active} 生效，reserve() 报 41016
     * 「预留不合法」，看着像超卖判定写坏了，其实是造数串台（表现为偶发红，且隔离连跑几乎必红）。
     *
     * <p>改用预留表自己的序列取值：序列值不回收，乘 100 拉开间距，保证相邻两次调用至少差 100，
     * 够每个用例取 +1..+3 而互不重叠；再与 {@code MAX(行号)} 取 GREATEST，跨过改动前就已在库里的遗留行。
     *
     * <p>用 {@code COALESCE} 而不是 SQL 的 {@code ?:} 简写：简写里的 {@code ?}
     * 会被 JDBC 驱动当成参数占位符，StatementCallback 直接报语法错误。
     */
    private Long sourceIdSeed() {
        return jdbc.queryForObject(
                "SELECT GREATEST(nextval('inventory_reservation_id_seq'), "
                        + "(SELECT COALESCE(MAX(source_document_item_id), 0) FROM inventory_reservation) / 100 + 1) * 100",
                Long.class);
    }

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    /**
     * 子线程没有请求上下文，必须自己塞一个身份（{@code ScmOperator.current()} 依赖它）。
     */
    private static void setThreadOperator() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Reservation concurrency IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        // 与种子员工 1 的真实行一致：id 1 是 V3 播种的 break-glass 超管。本用例测的是竞态，
        // 断言不能对仓库授权范围守卫敏感——缺了这一位，预留与释放会被按「无任何授权」拒掉，
        // 竞态根本发生不了，失败的那笔还会留下未释放的预留行污染后面的用例。
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private record Outcome(boolean success, Long id, Throwable error) {

        static Outcome ok(Long id) {
            return new Outcome(true, id, null);
        }

        static Outcome failed(Throwable error) {
            return new Outcome(false, null, error);
        }
    }

    /**
     * 让 N 段动作在同一起跑线开始，各自跑在**独立事务**里。
     */
    private List<Outcome> runConcurrently(List<java.util.concurrent.Callable<Long>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Long>> futures = new ArrayList<>(tasks.size());
            for (java.util.concurrent.Callable<Long> task : tasks) {
                futures.add(pool.submit(() -> {
                    setThreadOperator();
                    start.await();
                    try {
                        return task.call();
                    } finally {
                        SmartRequestUtil.remove();
                    }
                }));
            }
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>(futures.size());
            for (Future<Long> future : futures) {
                try {
                    outcomes.add(Outcome.ok(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)));
                } catch (ExecutionException e) {
                    outcomes.add(Outcome.failed(e.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Outcome> reserveTogether(Long warehouseId, Long skuId, Long sourceOrderId,
                                          List<String> quantities, List<Long> itemIds) throws Exception {
        List<java.util.concurrent.Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < quantities.size(); i++) {
            BigDecimal quantity = new BigDecimal(quantities.get(i));
            Long itemId = itemIds.get(i);
            tasks.add(() -> reservations.reserve(new ReserveInventoryFact(
                    warehouseId, skuId, "SALES_ORDER_ITEM", sourceOrderId, itemId,
                    quantity, OffsetDateTime.now(), null)));
        }
        return runConcurrently(tasks);
    }

    /**
     * 入库 {@code quantity} 后的 (仓库, SKU)，物理量已落账。
     */
    private Object[] stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        Long warehouseId = seedWarehouseId();
        assertThat(balanceRow(warehouseId, skuId)).as("前置：造数入库未落账").isNotNull();
        return new Object[]{warehouseId, skuId};
    }

    private BigDecimal reserved(Long warehouseId, Long skuId) {
        return balanceRow(warehouseId, skuId).getReservedQuantity();
    }

    private BigDecimal onHand(Long warehouseId, Long skuId) {
        return balanceRow(warehouseId, skuId).getQuantity();
    }

    private int reservationCountByStatus(Long warehouseId, Long skuId, String status) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM inventory_reservation "
                        + "WHERE warehouse_id = ? AND sku_id = ? AND status = ? AND deleted = FALSE",
                Integer.class, warehouseId, skuId, status);
        return count == null ? 0 : count;
    }

    /**
     * 该来源订单行留下的未删除预留行数。故意<b>不按 status 过滤</b>：
     * 「被拒的那笔不该留任何行」要比「不该留 ACTIVE 行」更严——留一行 RELEASED 同样是漏写。
     */
    private int reservationRowsForItem(Long itemId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM inventory_reservation "
                        + "WHERE source_document_type = 'SALES_ORDER_ITEM' "
                        + "AND source_document_item_id = ? AND deleted = FALSE",
                Integer.class, itemId);
        return count == null ? 0 : count;
    }

    /**
     * 可用量（现有量 − 预留量），直接读余额行两列现算，不取任何缓存视图。
     */
    private BigDecimal available(Long warehouseId, Long skuId) {
        InventoryBalanceEntity balance = balanceRow(warehouseId, skuId);
        return balance.getQuantity().subtract(balance.getReservedQuantity());
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("并发预留不超卖：3×4 抢 10 → 恰 2 成功、1 报 41011，占用量恒不超过现有量")
    void concurrentReservesCannotOversell() throws Exception {
        Object[] s = stocked("rc1", "10.0000");
        Long warehouseId = (Long) s[0];
        Long skuId = (Long) s[1];
        Long order = sourceIdSeed();
        List<Long> items = List.of(order + 1, order + 2, order + 3);

        List<Outcome> outcomes = reserveTogether(warehouseId, skuId, order,
                List.of("4.0000", "4.0000", "4.0000"), items);

        long succeeded = outcomes.stream().filter(Outcome::success).count();
        assertThat(succeeded)
                .as("总需求 12 > 现有量 10：只允许 2 笔落地，成功数不是 2 说明可用量判断跑在锁之外")
                .isEqualTo(2);
        outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                        .as("被拒的那笔必须是可用量不足（41011），不能是死锁或版本冲突", outcome.error())
                        .isEqualTo(41011));

        assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("8.0000");
        assertThat(onHand(warehouseId, skuId)).as("预留不改变物理库存").isEqualByComparingTo("10.0000");
        // 哪一笔被拒取决于哪个线程最后抢到余额行锁，不能假定就是提交顺序里的最后一笔：
        // outcomes 与 itemIds 按下标一一对应，据此定位真正失败的那几笔来查残留。
        for (int i = 0; i < items.size(); i++) {
            if (!outcomes.get(i).success())
                assertThat(reservationRowsForItem(items.get(i)))
                        .as("被拒的那笔（%s）不得留下预留行", items.get(i))
                        .isZero();
        }
        assertThat(reserved(warehouseId, skuId))
                .as("ck_inventory_balance_available 的口径：占用不得超过现有量")
                .isLessThan(onHand(warehouseId, skuId));
    }

    @Test
    @DisplayName("同一订单行并发重复预留：只落 1 行 ACTIVE，占用只加一次")
    void concurrentReserveForSameSourceLineCountsOnce() throws Exception {
        Object[] s = stocked("rc2", "10.0000");
        Long warehouseId = (Long) s[0];
        Long skuId = (Long) s[1];
        Long order = sourceIdSeed();
        Long itemId = order + 10;

        // 同一来源行、两笔都放得下（3+3 ≤ 10）—— 于是唯一能拦住第二笔的就是防重索引。
        List<Outcome> outcomes = reserveTogether(warehouseId, skuId, order,
                List.of("3.0000", "3.0000"), List.of(itemId, itemId));

        assertThat(outcomes.stream().filter(Outcome::success).count())
                .as("重复预留必须恰一成功")
                .isEqualTo(1);
        assertThat(reservationRowsForItem(itemId))
                .as("软删也算第二行：同一来源行只能有一条活动预留")
                .isEqualTo(1);
        assertThat(reserved(warehouseId, skuId))
                .as("占用量必须只加一次，双计会让后续可用量静默偏小")
                .isEqualByComparingTo("3.0000");
        outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                        .as("重复预留必须报 41016", outcome.error())
                        .isEqualTo(41016));
        assertLedgerBalanced(warehouseId, skuId);
    }

    @Test
    @DisplayName("并发释放同一条预留：恰一次回补，可用量不被虚增也不为负")
    void concurrentReleaseOfSameReservationIsCountedOnce() throws Exception {
        Object[] s = stocked("rc3", "10.0000");
        Long warehouseId = (Long) s[0];
        Long skuId = (Long) s[1];
        Long order = sourceIdSeed();
        List<Outcome> reservedOutcome = reserveTogether(warehouseId, skuId, order,
                List.of("4.0000"), List.of(order + 20));
        Long reservationId = reservedOutcome.getFirst().id();
        assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("4.0000");

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> {
                    reservations.release(reservationId);
                    return reservationId;
                },
                () -> {
                    reservations.release(reservationId);
                    return reservationId;
                }));

        assertThat(outcomes.stream().filter(Outcome::success).count())
                .as("释放必须恰一次生效")
                .isEqualTo(1);
        assertThat(reserved(warehouseId, skuId))
                .as("重复回补会让 reserved_quantity 变负或残留 —— 两者都是错账")
                .isEqualByComparingTo("0.0000");
        assertThat(available(warehouseId, skuId)).isEqualByComparingTo("10.0000");
        assertThat(reservationCountByStatus(warehouseId, skuId, "RELEASED")).isEqualTo(1);
        outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                        .as("第二次的失败必须来自状态条件（41016），而不是余额行消失", outcome.error())
                        .isEqualTo(41016));
    }

    @Test
    @DisplayName("并发释放同仓同 SKU 的两条预留：两次都回补，可用量恢复后出库能出满")
    void concurrentReleaseOfDifferentReservationsRestoresAvailability() throws Exception {
        Object[] s = stocked("rc4", "10.0000");
        Long warehouseId = (Long) s[0];
        Long skuId = (Long) s[1];
        Long order = sourceIdSeed();
        List<Outcome> held = reserveTogether(warehouseId, skuId, order,
                List.of("3.0000", "4.0000"), List.of(order + 30, order + 31));
        Long first = held.get(0).id();
        Long second = held.get(1).id();
        assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("7.0000");

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> {
                    reservations.release(first);
                    return first;
                },
                () -> {
                    reservations.release(second);
                    return second;
                }));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.success())
                .as("两条互不相干的预留不应互相失败：%s", outcome.error())
                .isTrue());
        // 两次 decrement 都必须落地：丢失更新会残留 3 或 4 而不是 0
        assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("0.0000");
        assertThat(available(warehouseId, skuId)).isEqualByComparingTo("10.0000");
        assertThat(reservationCountByStatus(warehouseId, skuId, "ACTIVE")).isZero();

        // 可用量恢复必须是真实的：能一次性出满 10，才证明不是靠预留行残留撑出来的数字
        postSalesOutbound(new InventoryOutboundFact(
                warehouseId, skuId, 0L, order + 32, new BigDecimal("10.0000"), null,
                OffsetDateTime.now(), "test:1"));
        assertThat(onHand(warehouseId, skuId)).isEqualByComparingTo("0.0000");
        assertLedgerBalanced(warehouseId, skuId);
    }

    @Test
    @DisplayName("预留与出库并发抢同一余额行：可用量口径唯一，6+6 抢 10 → 恰一成功、剩 4 可用")
    void reserveAndOutboundShareOneAvailableQuantityGate() throws Exception {
        Object[] s = stocked("rc5", "10.0000");
        Long warehouseId = (Long) s[0];
        Long skuId = (Long) s[1];
        Long order = sourceIdSeed();
        Long outboundItemId = order + 40;

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> reservations.reserve(new ReserveInventoryFact(
                        warehouseId, skuId, "SALES_ORDER_ITEM", order, order + 41,
                        new BigDecimal("6.0000"), OffsetDateTime.now(), null)),
                () -> {
                    postSalesOutbound(new InventoryOutboundFact(
                            warehouseId, skuId, 0L, outboundItemId, new BigDecimal("6.0000"), null,
                            OffsetDateTime.now(), "test:1"));
                    return null;
                }));

        long succeeded = outcomes.stream().filter(Outcome::success).count();
        assertThat(succeeded)
                .as("占用与出库共用「可用量 = 现有量 − 预留量」一个口径：两者合计 12 > 10，只能成一个")
                .isEqualTo(1);
        outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                        .as("输的那一方必须是被可用量门槛拒绝（41011），不能是「缺少事务」这类脚手架异常", outcome.error())
                        .isEqualTo(41011));
        assertThat(available(warehouseId, skuId)).isEqualByComparingTo("4.0000");
        assertThat(reserved(warehouseId, skuId))
                .as("reserved_quantity <= quantity 在竞态后仍成立")
                .isLessThanOrEqualTo(onHand(warehouseId, skuId));
        assertLedgerBalanced(warehouseId, skuId);

        List<Map<String, Object>> movements = movementsOf(warehouseId, skuId);
        long salesOut = movements.stream()
                .filter(row -> "SALES_OUT".equals(String.valueOf(row.get("movement_type")))).count();
        assertThat(salesOut)
                .as("失败的出库不能留下流水（预留不写流水，出库只有一个来源行成功）")
                .isLessThanOrEqualTo(1);
    }

    /**
     * 从异常里取 SCM 业务错误码；非业务异常返回哨兵值，让断言报出真实原因而不是 NPE。
     */
    private static final java.util.function.Function<Throwable, Integer> SCM_ERROR_CODE = error -> {
        if (error instanceof net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException e) {
            return e.getErrorCode().getCode();
        }
        return -1;
    };
}
