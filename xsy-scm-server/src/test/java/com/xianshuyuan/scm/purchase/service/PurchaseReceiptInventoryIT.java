package com.xianshuyuan.scm.purchase.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.inventory.service.InventoryApplicationService;
import com.xianshuyuan.scm.inventory.service.PurchaseInCommand;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptConfirmItemRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptConfirmRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseReceiptCreateRequest;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderItemEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderStatus;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptStatus;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseReceiptItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseReceiptMapper;
import com.xianshuyuan.scm.purchase.vo.PurchaseReceiptConfirmResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PurchaseReceiptInventoryIT {
    @Autowired
    private PurchaseReceiptApplicationService receiptsService;

    @Autowired
    private InventoryApplicationService inventoryService;

    @Autowired
    private PurchaseOrderMapper orders;

    @Autowired
    private PurchaseOrderItemMapper orderItems;

    @Autowired
    private PurchaseReceiptMapper receipts;

    @Autowired
    private PurchaseReceiptItemMapper receiptItems;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void confirmsTwoBatchesAndReplaysWithoutDuplicateInventoryPosting() {
        Fixture fixture = fixture("10.0000");
        long receiptId = receiptsService.create(new PurchaseReceiptCreateRequest(fixture.orderId(), "integration"));
        var receiptItem = receiptItems.selectActiveByReceiptId(receiptId).getFirst();

        PurchaseReceiptConfirmRequest firstRequest = request(receiptItem.getId(), receiptItem.getVersion(), "4.0000");
        PurchaseReceiptConfirmResult first = receiptsService.confirm(receiptId, firstRequest, fixture.key("partial"));
        PurchaseReceiptConfirmResult replay = receiptsService.confirm(receiptId, firstRequest, fixture.key("partial"));

        assertThat(first.status()).isEqualTo("PARTIALLY_CONFIRMED");
        assertThat(first.purchaseOrderStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(replay).isEqualTo(first);
        assertThat(quantity("purchase_order_item", fixture.orderItemId())).isEqualByComparingTo("4.0000");
        assertThat(quantity("purchase_receipt_item", receiptItem.getId())).isEqualByComparingTo("4.0000");
        assertThat(inventoryQuantity(fixture)).isEqualByComparingTo("4.0000");
        assertThat(movementCount(receiptId)).isOne();

        PurchaseReceiptEntity partialReceipt = receipts.selectActiveById(receiptId);
        var partialItem = receiptItems.selectActiveByReceiptId(receiptId).getFirst();
        PurchaseReceiptConfirmResult second = receiptsService.confirm(receiptId,
                request(partialItem.getId(), partialItem.getVersion(), "6.0000"), fixture.key("final"));

        assertThat(second.status()).isEqualTo("CONFIRMED");
        assertThat(second.purchaseOrderStatus()).isEqualTo("RECEIVED");
        assertThat(partialReceipt.getStatus()).isEqualTo(PurchaseReceiptStatus.PARTIALLY_CONFIRMED);
        assertThat(quantity("purchase_order_item", fixture.orderItemId())).isEqualByComparingTo("10.0000");
        assertThat(quantity("purchase_receipt_item", receiptItem.getId())).isEqualByComparingTo("10.0000");
        assertThat(inventoryQuantity(fixture)).isEqualByComparingTo("10.0000");
        assertThat(movementCount(receiptId)).isEqualTo(2);
        assertThat(confirmationCount(receiptId)).isEqualTo(2);
        assertThat(movementArithmeticViolations(receiptId)).isZero();
    }

    @Test
    void rejectsOverReceiptAndSameKeyWithDifferentRequestWithoutAnyPartialWrite() {
        Fixture fixture = fixture("5.0000");
        long receiptId = receiptsService.create(new PurchaseReceiptCreateRequest(fixture.orderId(), null));
        var receiptItem = receiptItems.selectActiveByReceiptId(receiptId).getFirst();
        String key = fixture.key("same");

        receiptsService.confirm(receiptId, request(receiptItem.getId(), receiptItem.getVersion(), "3.0000"), key);
        var currentItem = receiptItems.selectActiveByReceiptId(receiptId).getFirst();

        assertThatThrownBy(() -> receiptsService.confirm(
                receiptId, request(currentItem.getId(), currentItem.getVersion(), "1.0000"), key))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> receiptsService.confirm(
                receiptId, request(currentItem.getId(), currentItem.getVersion(), "2.5001"), fixture.key("over")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PurchaseReceiptErrorCodes.OVER_RECEIVED));

        assertThat(quantity("purchase_order_item", fixture.orderItemId())).isEqualByComparingTo("3.0000");
        assertThat(inventoryQuantity(fixture)).isEqualByComparingTo("3.0000");
        assertThat(movementCount(receiptId)).isOne();
        assertThat(confirmationCount(receiptId)).isOne();
        assertThat(idempotencyCount(fixture.key("over"))).isZero();
    }

    @Test
    void rollsBackReceiptAndInventoryWhenLateReceiptUpdateFails() {
        Fixture fixture = fixture("4.0000");
        long receiptId = receiptsService.create(new PurchaseReceiptCreateRequest(fixture.orderId(), null));
        var receiptItem = receiptItems.selectActiveByReceiptId(receiptId).getFirst();
        String key = fixture.key("rollback");

        jdbcTemplate.execute("""
                create or replace function fail_test_receipt_update()
                returns trigger language plpgsql as $$
                begin
                    if new.id = %d then
                        raise exception 'forced late receipt update failure';
                    end if;
                    return new;
                end;
                $$
                """.formatted(receiptId));
        jdbcTemplate.execute("""
                create trigger trg_fail_test_receipt_update
                before update on purchase_receipt
                for each row execute function fail_test_receipt_update()
                """);
        try {
            assertThatThrownBy(() -> receiptsService.confirm(
                    receiptId, request(receiptItem.getId(), receiptItem.getVersion(), "2.0000"), key))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("drop trigger if exists trg_fail_test_receipt_update on purchase_receipt");
            jdbcTemplate.execute("drop function if exists fail_test_receipt_update()");
        }

        assertThat(quantity("purchase_order_item", fixture.orderItemId())).isEqualByComparingTo("0.0000");
        assertThat(quantity("purchase_receipt_item", receiptItem.getId())).isEqualByComparingTo("0.0000");
        assertThat(inventoryCount(fixture)).isZero();
        assertThat(movementCount(receiptId)).isZero();
        assertThat(confirmationCount(receiptId)).isZero();
        assertThat(idempotencyCount(key)).isZero();
    }

    @Test
    void serializesConcurrentRemainderConsumptionWithoutOverReceipt() throws Exception {
        Fixture fixture = fixture("5.0000");
        long receiptId = receiptsService.create(new PurchaseReceiptCreateRequest(fixture.orderId(), null));
        var receiptItem = receiptItems.selectActiveByReceiptId(receiptId).getFirst();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> confirmAfterSignal(
                    ready, start, receiptId, receiptItem.getId(), receiptItem.getVersion(), "3.0000",
                    fixture.key("concurrent-a")));
            var second = executor.submit(() -> confirmAfterSignal(
                    ready, start, receiptId, receiptItem.getId(), receiptItem.getVersion(), "3.0000",
                    fixture.key("concurrent-b")));
            ready.await();
            start.countDown();

            var outcomes = java.util.List.of(first.get(), second.get());
            assertThat(outcomes).filteredOn(ConcurrentOutcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.success()).hasSize(1);
        }

        assertThat(quantity("purchase_order_item", fixture.orderItemId())).isEqualByComparingTo("3.0000");
        assertThat(inventoryQuantity(fixture)).isEqualByComparingTo("3.0000");
        assertThat(movementCount(receiptId)).isOne();
        assertThat(confirmationCount(receiptId)).isOne();
    }

    @Test
    void serializesConcurrentFirstBalanceCreationAndKeepsMovementArithmetic() throws Exception {
        long warehouseId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        long skuId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        if (warehouseId == 0) {
            warehouseId = 1;
        }
        if (skuId == 0) {
            skuId = 1;
        }
        long finalWarehouseId = warehouseId;
        long finalSkuId = skuId;
        long firstSource = positiveRandomLong();
        long secondSource = positiveRandomLong();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> postAfterSignal(ready, start,
                    inventoryCommand(firstSource, firstSource, firstSource, finalWarehouseId, finalSkuId, "2.0000")));
            var second = executor.submit(() -> postAfterSignal(ready, start,
                    inventoryCommand(secondSource, secondSource, secondSource, finalWarehouseId, finalSkuId, "3.0000")));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory where warehouse_id = ? and sku_id = ? and deleted = false",
                Integer.class, warehouseId, skuId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select quantity from inventory where warehouse_id = ? and sku_id = ? and deleted = false",
                BigDecimal.class, warehouseId, skuId)).isEqualByComparingTo("5.0000");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from inventory_movement
                where warehouse_id = ? and sku_id = ?
                  and quantity_after = quantity_before + quantity_change
                """, Integer.class, warehouseId, skuId)).isEqualTo(2);
    }

    @Test
    void requiresAnExistingTransactionForInventoryPosting() {
        PurchaseInCommand command = new PurchaseInCommand(1L, 1L, 1L, 1L, 1L,
                "W", "Warehouse", "SKU", "Product", "kg",
                BigDecimal.ONE, BigDecimal.ONE);

        assertThatThrownBy(() -> inventoryService.postPurchaseIn(command))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Fixture fixture(String plannedQuantity) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long seed = Math.abs(UUID.randomUUID().getMostSignificantBits());
        if (seed == 0) {
            seed = 1;
        }

        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setOrderNo("IT-PO-" + token);
        order.setSupplierId(seed);
        order.setSupplierCodeSnapshot("IT-S-" + token);
        order.setSupplierNameSnapshot("Integration Supplier");
        order.setWarehouseId(seed);
        order.setWarehouseCodeSnapshot("IT-W-" + token);
        order.setWarehouseNameSnapshot("Integration Warehouse");
        order.setStatus(PurchaseOrderStatus.SUBMITTED);
        order.setTotalAmount(new BigDecimal(plannedQuantity).multiply(new BigDecimal("3.5000")));
        order.setVersion(0);
        order.setDeleted(false);
        order.setCreatedBy("SYSTEM");
        orders.insert(order);

        PurchaseOrderItemEntity item = new PurchaseOrderItemEntity();
        item.setPurchaseOrderId(order.getId());
        item.setSpuId(seed);
        item.setSkuId(seed);
        item.setSpuCodeSnapshot("IT-SPU-" + token);
        item.setProductNameSnapshot("Integration Product");
        item.setSkuCodeSnapshot("IT-SKU-" + token);
        item.setSkuNameSnapshot("Integration SKU");
        item.setSpecValuesSnapshot(Map.of());
        item.setPurchaseUnitSnapshot("kg");
        item.setProductTypeSnapshot(ProductType.STANDARD);
        item.setPlannedQuantity(new BigDecimal(plannedQuantity));
        item.setReceivedQuantity(new BigDecimal("0.0000"));
        item.setPurchasePrice(new BigDecimal("3.5000"));
        item.setLineAmount(order.getTotalAmount());
        item.setSortOrder(0);
        item.setVersion(0);
        item.setDeleted(false);
        item.setCreatedBy("SYSTEM");
        orderItems.insert(item);
        return new Fixture(order.getId(), item.getId(), seed, seed, token);
    }

    private PurchaseReceiptConfirmRequest request(long receiptItemId, int version, String quantity) {
        return new PurchaseReceiptConfirmRequest(version, java.util.List.of(
                new PurchaseReceiptConfirmItemRequest(receiptItemId, version, quantity, null, null, null)));
    }

    private BigDecimal quantity(String table, long id) {
        if (!table.equals("purchase_order_item") && !table.equals("purchase_receipt_item")) {
            throw new IllegalArgumentException("Unsupported table");
        }
        return jdbcTemplate.queryForObject("select received_quantity from " + table + " where id = ?",
                BigDecimal.class, id);
    }

    private BigDecimal inventoryQuantity(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "select quantity from inventory where warehouse_id = ? and sku_id = ? and deleted = false",
                BigDecimal.class, fixture.warehouseId(), fixture.skuId());
    }

    private int inventoryCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "select count(*) from inventory where warehouse_id = ? and sku_id = ? and deleted = false",
                Integer.class, fixture.warehouseId(), fixture.skuId());
    }

    private int movementCount(long receiptId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from inventory_movement where source_document_id = ?", Integer.class, receiptId);
    }

    private int confirmationCount(long receiptId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from purchase_receipt_confirmation where purchase_receipt_id = ?",
                Integer.class, receiptId);
    }

    private int movementArithmeticViolations(long receiptId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from inventory_movement
                where source_document_id = ?
                  and quantity_after <> quantity_before + quantity_change
                """, Integer.class, receiptId);
    }

    private int idempotencyCount(String key) {
        return jdbcTemplate.queryForObject(
                "select count(*) from idempotency_record where idempotency_key = ? and deleted = false",
                Integer.class, key);
    }

    private long positiveRandomLong() {
        long value = Math.abs(UUID.randomUUID().getMostSignificantBits());
        return value == 0 ? 1 : value;
    }

    private ConcurrentOutcome confirmAfterSignal(
            CountDownLatch ready,
            CountDownLatch start,
            long receiptId,
            long receiptItemId,
            int version,
            String quantity,
            String key
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            receiptsService.confirm(receiptId, request(receiptItemId, version, quantity), key);
            return new ConcurrentOutcome(true);
        } catch (BusinessException error) {
            return new ConcurrentOutcome(false);
        }
    }

    private void postAfterSignal(
            CountDownLatch ready,
            CountDownLatch start,
            PurchaseInCommand command
    ) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
        transactionTemplate.executeWithoutResult(ignored -> inventoryService.postPurchaseIn(command));
    }

    private PurchaseInCommand inventoryCommand(
            long receiptId,
            long receiptItemId,
            long confirmationId,
            long warehouseId,
            long skuId,
            String quantity
    ) {
        return new PurchaseInCommand(receiptId, receiptItemId, confirmationId, warehouseId, skuId,
                "IT-W", "Integration Warehouse", "IT-SKU", "Integration SKU", "kg",
                new BigDecimal(quantity), new BigDecimal("3.5000"));
    }

    private record ConcurrentOutcome(boolean success) {
    }

    private record Fixture(long orderId, long orderItemId, long warehouseId, long skuId, String token) {
        private String key(String suffix) {
            return "IT-" + token + "-" + suffix;
        }
    }
}
