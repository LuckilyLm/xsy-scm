package com.xsy.scm.purchase;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;
import com.xsy.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderCancelForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 需求分配与采购单取消的并发（PUR-01，PG IT，无外层事务）。
 *
 * <p>两条命令的锁序原本不一致，这是本类存在的全部理由：
 * <ul>
 * <li>{@code demand.allocate}：修复前是「锁需求（第 1 层）→ 无锁读采购单」；</li>
 * <li>{@code purchase_order.cancel}：锁采购单 → 释放分配（内含锁需求，升序）→ 置 CANCELLED。</li>
 * </ul>
 *
 * <p>修复前的幻读窗口：{@code cancel} 读「本单没有分配」→ 无分配时早退、不取需求锁 →
 * {@code allocate} 插入并提交 → {@code cancel} 提交 CANCELLED。结果是一条挂在已 CANCELLED
 * 采购单上的活分配，且需求 {@code allocated_quantity} 只增不减。
 *
 * <p>修复方式是把 {@code allocate} 改成与其它命令**同向**：先锁采购单行、再锁需求行。
 * 于是分配与取消在单据行上串行，窗口不存在。早期「先取单锁会与 update 成环」的判断是错的 ——
 * update 本来就是「单 → 需求」，同向才是对齐。
 *
 * <p>无外层事务（{@link Propagation#NOT_SUPPORTED}）是刻意的：真提交才能让另一个线程看见，
 * 外层事务会把两边都塞进同一个回滚域里，测不出任何东西。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("需求分配 vs 采购单取消：状态重读与锁序（PUR-01，PG IT，无外层事务）")
class PurchaseAllocationCancelRacePgIT extends ScmW5PgITBase {

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    /** 一次分配的请求体；{@code quantity} 用字符串，与 API 口径一致。 */
    private PurchaseDemandAllocateForm allocateForm(PurchaseDemandEntity demand, Long orderItemId,
                                                    String quantity) {
        PurchaseDemandAllocateForm form = new PurchaseDemandAllocateForm();
        form.setDemandId(demand.getId());
        form.setPurchaseOrderItemId(orderItemId);
        form.setQuantity(quantity);
        form.setVersion(demand.getVersion());
        // supplier / warehouse 是表单必填项（与需求上固定的那对一致），服务端还会再校验一致性
        form.setSupplierId(demand.getSupplierId());
        form.setWarehouseId(demand.getWarehouseId());
        return form;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    /** 采购单唯一一行的 id（单行夹具，取第一行即可）。 */
    private Long firstItemId(Long orderId) {
        return reloadOrder(orderId).getItems().getFirst().getId();
    }

    private void loginAsAdmin(String name) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName(name);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    // ------------------------------------------------------------------
    // 1. 顺序复现：取消已提交之后，分配必须被拒（窗口 (C) 的确定性版本）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("采购单已取消后再分配 → 40982，且不留下任何活动分配")
    void allocateIsRejectedOnAnAlreadyCancelledOrder() {
        Fixture fx = fixture("PARC1", "10.0000", "10.0000");
        PurchaseDemandEntity demand = generateDemandFor(fx.supplierId(), fx.salesOrderId());
        PurchaseOrderVO order = createDraftOrder("PARCPO1", fx.supplierId(), fx.skuId(), "10.0000", "3.0000");
        PurchaseOrderVO submitted = submitOrder(order.getId());
        Long itemId = firstItemId(order.getId());

        // 取消：释放分配 + 置 CANCELLED，全部提交（无外层事务）
        var cancel = new PurchaseOrderCancelForm();
        cancel.setId(submitted.getId());
        cancel.setVersion(reloadOrder(order.getId()).getVersion());
        cancel.setCancelReason("PUR-01 用例：取消后不允许再分配");
        purchaseOrderService.cancel(cancel, key("cancel"));

        assertThat(reloadOrder(order.getId()).getStatus()).isEqualTo("CANCELLED");

        // 取消后的一切分配尝试都必须被状态门禁拦住
        PurchaseDemandEntity current = reloadDemand(demand.getId());
        expectCode(() -> purchaseDemandService.allocate(allocateForm(current, itemId, "5.0000"), key("alloc")),
                40982);

        assertThat(allocationsOf(order.getId()))
                .as("已取消的采购单不得留下任何活动分配").isEmpty();
    }

    // ------------------------------------------------------------------
    // 2. 真并发：取消 vs 分配同时冲进去
    // ------------------------------------------------------------------

    @Test
    @DisplayName("取消与分配真并发：要么取消先赢（分配 40982）、要么分配先赢（取消仍把分配清干净），绝不死锁")
    void cancelAndAllocateRaceWithoutDeadlock() throws Exception {
        Fixture fx = fixture("PARC2", "10.0000", "10.0000");
        PurchaseDemandEntity demand = generateDemandFor(fx.supplierId(), fx.salesOrderId());
        PurchaseOrderVO order = createDraftOrder("PARCPO2", fx.supplierId(), fx.skuId(), "10.0000", "3.0000");
        PurchaseOrderVO submitted = submitOrder(order.getId());
        Long itemId = firstItemId(order.getId());

        var cancelFailure = new AtomicReference<Throwable>();
        var allocateFailure = new AtomicReference<Throwable>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        // 取消侧：版本要在自己线程内回读（另一线程可能已改动单据）
        start("cancel-thread", () -> {
            var cancel = new PurchaseOrderCancelForm();
            cancel.setId(submitted.getId());
            cancel.setVersion(reloadOrder(order.getId()).getVersion());
            cancel.setCancelReason("PUR-01 并发用例");
            purchaseOrderService.cancel(cancel, key("race-cancel"));
        }, cancelFailure, ready, start, done);

        start("allocate-thread", () -> {
            PurchaseDemandEntity current = reloadDemand(demand.getId());
            purchaseDemandService.allocate(allocateForm(current, itemId, "5.0000"), key("race-alloc"));
        }, allocateFailure, ready, start, done);

        assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS))
                .as("两个事务必须在 60 秒内结束（超时即疑似死锁）").isTrue();

        // 无论谁先赢，最终不变量：单据 CANCELLED，且这条已取消的单上没有任何活动分配。
        assertThat(reloadOrder(order.getId()).getStatus())
                .as("取消侧不应失败：它只锁自己的单据，与分配侧不构成环").isEqualTo("CANCELLED");
        assertThat(cancelFailure.get()).as("取消一侧不该抛异常：%s", cancelFailure.get()).isNull();

        // 分配侧只有两种合法结局：先赢（成功）或后输（40982）。
        if (allocateFailure.get() != null) {
            assertThat(allocateFailure.get())
                    .as("后到的分配必须是被状态门禁拒绝的业务异常")
                    .isInstanceOf(ScmBusinessException.class);
        }

        assertThat(allocationsOf(order.getId()))
                .as("无论顺序如何，取消都必须把这一单上的分配清干净 —— "
                        + "这正是修复前「取消读到没有分配 → 早退 → 分配随后插进来」留下的漏网")
                .isEmpty();

        PurchaseDemandEntity finalDemand = reloadDemand(demand.getId());
        assertThat(finalDemand.getAllocatedQuantity())
                .as("需求 allocated_quantity 必须回落到 0：分配侧若要落库必然发生在取消之前，取消必然把它释放掉")
                .isEqualByComparingTo("0.0000");
        assertThat(finalDemand.getStatus())
                .as("分配全部释放后需求退回 PENDING").isEqualTo("PENDING");
    }

    /**
     * 起一个线程：登录 → 到起跑线 → 等号令 → 跑 → 记异常。
     *
     * <p>与 {@code ScmFinancePaymentRacePgIT} 同一套编排，保持仓库内并发用例一致。
     */
    private void start(String name, Runnable body, AtomicReference<Throwable> failure,
                       CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
        new Thread(() -> {
            loginAsAdmin(name);
            try {
                ready.countDown();
                start.await();
                body.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                SmartRequestUtil.remove();
                done.countDown();
            }
        }, name).start();
    }
}
