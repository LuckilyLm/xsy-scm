package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发入库确认（B1，PG IT，无外层事务）。
 *
 * <p>两个线程对同一张 {@code WAREHOUSE_CONFIRM + PENDING} 收货单并发 putaway：
 * 收货单行锁把两者串行化，**只有一个**能通过 {@code putaway_status=PENDING} 校验并写库存，
 * 另一个要么版本冲突（40921）要么状态冲突（41008），绝不重复 PURCHASE_IN。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("并发入库确认（PG IT，无外层事务）")
class PurchaseReceiptPutawayConcurrencyIT extends ScmW6PgITBase {

    private static final long TIMEOUT_SECONDS = 60;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private static void setThreadOperator() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("B1 concurrent IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        // 与种子员工 1 的真实行一致：本用例测的是并发两次上架的竞态，不是仓库授权。
        // 缺了这一位，上架的仓库范围守卫会按「无任何授权」拒掉两次调用，竞态根本发生不了。
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private record Outcome(boolean success, Throwable error) {
        static Outcome ok() {
            return new Outcome(true, null);
        }

        static Outcome failed(Throwable error) {
            return new Outcome(false, error);
        }
    }

    private List<Outcome> runConcurrently(Runnable first, Runnable second) throws Exception {
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

    @Test
    @DisplayName("并发 putaway 同一收货单：恰好一次成功、一次失败、仅一条流水")
    void concurrentPutawayProducesExactlyOneInbound() throws Exception {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("PC1");
        Long supplierId = newPurchasableSupplier("PC1", skuId);
        PurchaseOrderVO order = createDraftOrder("PC1", supplierId, skuId, "6.0000", "6.2000");
        submitOrder(order.getId());

        PurchaseReceiptCreateForm create = new PurchaseReceiptCreateForm();
        create.setPurchaseOrderId(order.getId());
        create.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
        create.setRemark("B1 并发入库收货单");
        PurchaseReceiptVO receipt = purchaseReceiptService.create(create, prefix + ":PC1:receipt");
        confirmReceipt(receipt.getId(), "6.0000");

        List<Outcome> outcomes = runConcurrently(
                () -> putaway(receipt.getId()),
                () -> putaway(receipt.getId()));

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.success()).hasSize(1);

        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("6.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(movementsOfReceiptItem(receipt.getItems().getFirst().getId())).isEqualTo(1);
    }

    private void putaway(Long receiptId) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
        form.setId(receiptId);
        form.setVersion(current.getVersion());
        purchaseReceiptService.putaway(form, prefix + ":PC1:putaway:" + UUID.randomUUID());
    }
}
