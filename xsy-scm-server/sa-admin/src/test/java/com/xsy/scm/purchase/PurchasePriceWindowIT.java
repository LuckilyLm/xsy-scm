package com.xsy.scm.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderLockForm;
import com.xsy.scm.purchase.domain.form.PurchasePriceForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.job.PurchaseOrderAutoLockJob;
import com.xsy.scm.purchase.job.PurchasePriceAutoFillJob;
import com.xsy.scm.purchase.service.PurchaseOrderLockService;
import com.xsy.scm.purchase.service.PurchasePriceService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 采购价后置与采购锁单。
 *
 * <p>窗口从提交时刻起算 7 天：窗口内补价、窗口外只能改价；超期自动回填只取最近一次已提交价，
 * 没有参考价时保持待定价。收货确认前必须所有行都有价，避免把成本记成 0。
 */
class PurchasePriceWindowIT extends ScmW6PgITBase {

    @Autowired
    private PurchasePriceService purchasePriceService;

    @Autowired
    private PurchaseOrderLockService purchaseOrderLockService;

    @Autowired
    private PurchasePriceAutoFillJob purchasePriceAutoFillJob;

    @Autowired
    private PurchaseOrderAutoLockJob purchaseOrderAutoLockJob;

    @Test
    @DisplayName("#1 待定价可补价，补价前收货被拒（41067），确认收货后不能再改价（41075）")
    void fillPendingPriceThenReceive() {
        Long skuId = newOnShelfSku("PW1");
        Long supplierId = newPurchasableSupplier("PW1", skuId);
        PurchaseOrderVO order = createPendingOrder("PW1", supplierId, skuId, "5.0000");
        order = submitOrder(order.getId());

        var item = order.getItems().getFirst();
        assertThat(item.getPricePending()).isTrue();
        assertThat(item.getPurchasePrice()).isNull();
        assertThat(item.getLineAmount()).isEqualByComparingTo("0.0000");

        var receipt = createReceipt(order.getId());
        var current = reloadReceipt(receipt.getId());
        var line = current.getItems().getFirst();
        expectCode(() -> purchaseReceiptService.confirm(
            confirmForm(current.getId(), current.getVersion(),
                receiptLine(line.getId(), line.getVersion(), "5.0000")),
            prefix + ":receive-before-price"), 41067);

        PurchasePriceForm fill = new PurchasePriceForm();
        fill.setPrice("6.0000");
        fill.setReason("供应商当日未报价");
        purchasePriceService.fill(item.getId(), fill, prefix + ":fill:PW1");

        PurchaseOrderVO filled = reloadOrder(order.getId());
        assertThat(filled.getItems().getFirst().getPricePending()).isFalse();
        assertThat(filled.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("6.0000");
        assertThat(filled.getItems().getFirst().getLineAmount()).isEqualByComparingTo("30.0000");
        assertThat(filled.getTotalAmount()).isEqualByComparingTo("30.0000");
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM xsy_purchase_price_adjust WHERE purchase_order_item_id = ? "
                + "AND source = 'MANUAL' AND new_price = 6.0000",
            Integer.class, item.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM xsy_purchase_operation_log WHERE purchase_order_id = ? "
                + "AND operation_type = 'PRICE_FILL'",
            Integer.class, order.getId())).isEqualTo(1);

        confirmReceipt(receipt.getId(), "5.0000");
        assertThat(balanceRow(fixtureWarehouseId(), skuId).getQuantity()).isEqualByComparingTo("5.0000");

        PurchasePriceForm adjust = new PurchasePriceForm();
        adjust.setPrice("6.5000");
        adjust.setReason("发票差异");
        expectCode(() -> purchasePriceService.adjust(item.getId(), adjust, prefix + ":adjust:PW1"), 41075);
    }

    @Test
    @DisplayName("#2 窗口过期后补价被拒（41068），自动回填按最近已提交价并留审计")
    void expiredWindowFallsBackToLastSubmittedPrice() {
        Long skuId = newOnShelfSku("PW2");
        Long supplierId = newPurchasableSupplier("PW2", skuId);

        PurchaseOrderVO history = createDraftOrder("PW2-H", supplierId, skuId, "2.0000", "7.0000");
        submitOrder(history.getId());

        PurchaseOrderVO pending = createPendingOrder("PW2-P", supplierId, skuId, "3.0000");
        pending = submitOrder(pending.getId());
        Long pendingItemId = pending.getItems().getFirst().getId();

        jdbc.update("UPDATE xsy_purchase_order_item SET price_window_started_at = CURRENT_TIMESTAMP - INTERVAL '8 days' "
            + "WHERE id = ?", pendingItemId);
        jdbc.update("UPDATE xsy_purchase_order SET auto_lock_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?",
            pending.getId());
        evictMybatisCache();

        PurchasePriceForm fill = new PurchasePriceForm();
        fill.setPrice("6.5000");
        expectCode(() -> purchasePriceService.fill(pendingItemId, fill, prefix + ":fill:PW2"), 41068);

        purchasePriceAutoFillJob.autoFillExpiredPrices();

        PurchaseOrderVO filled = reloadOrder(pending.getId());
        assertThat(filled.getItems().getFirst().getPricePending()).isFalse();
        assertThat(filled.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("7.0000");
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM xsy_purchase_price_adjust WHERE purchase_order_item_id = ? "
                + "AND source = 'AUTO_LAST_PRICE'",
            Integer.class, pendingItemId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM xsy_purchase_operation_log WHERE purchase_order_id = ? "
                + "AND operation_type = 'PRICE_AUTO_FILL' AND operator = ?",
            Integer.class, pending.getId(), PurchasePriceService.AUTO_FILL_OPERATOR)).isEqualTo(1);
    }

    @Test
    @DisplayName("#3 锁单后不能补价（41069），解锁必须写原因（41077）；到期自动锁单")
    void lockBlocksPriceFillAndAutoLockRuns() {
        Long skuId = newOnShelfSku("PW3");
        Long supplierId = newPurchasableSupplier("PW3", skuId);
        PurchaseOrderVO order = submitOrder(createPendingOrder("PW3", supplierId, skuId, "4.0000").getId());
        Long itemId = order.getItems().getFirst().getId();

        PurchaseOrderLockForm lock = new PurchaseOrderLockForm();
        lock.setId(order.getId());
        lock.setVersion(reloadOrder(order.getId()).getVersion());
        lock.setReason("与供应商对账");
        purchaseOrderLockService.lock(lock);

        PurchasePriceForm fill = new PurchasePriceForm();
        fill.setPrice("8.0000");
        expectCode(() -> purchasePriceService.fill(itemId, fill, prefix + ":fill:PW3"), 41069);

        PurchaseOrderLockForm unlock = new PurchaseOrderLockForm();
        unlock.setId(order.getId());
        unlock.setVersion(reloadOrder(order.getId()).getVersion());
        expectCode(() -> purchaseOrderLockService.unlock(unlock), 41077);

        unlock.setReason("价格录错，重新补录");
        purchaseOrderLockService.unlock(unlock);
        purchasePriceService.fill(itemId, fill, prefix + ":fill:PW3-after-unlock");
        assertThat(reloadOrder(order.getId()).getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("8.0000");

        // 另一张到期单由定时任务自动锁上
        PurchaseOrderVO expired = submitOrder(createPendingOrder("PW3B", supplierId, skuId, "1.0000").getId());
        jdbc.update("UPDATE xsy_purchase_order SET auto_lock_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' "
            + "WHERE id = ?", expired.getId());
        evictMybatisCache();
        purchaseOrderAutoLockJob.lockExpiredOrders();

        assertThat(reloadOrder(expired.getId()).getLockedFlag()).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM xsy_purchase_operation_log WHERE purchase_order_id = ? "
                + "AND operation_type = 'PRICE_AUTO_LOCK'",
            Integer.class, expired.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("#4 到期但没有历史采购价时保持待定价，不写 0")
    void autoFillWithoutReferenceStaysPending() {
        Long skuId = newOnShelfSku("PW4");
        Long supplierId = newPurchasableSupplier("PW4", skuId);
        PurchaseOrderVO pending = submitOrder(createPendingOrder("PW4", supplierId, skuId, "2.0000").getId());
        Long itemId = pending.getItems().getFirst().getId();
        jdbc.update("UPDATE xsy_purchase_order_item SET price_window_started_at = CURRENT_TIMESTAMP - INTERVAL '8 days' "
            + "WHERE id = ?", itemId);
        evictMybatisCache();

        purchasePriceAutoFillJob.autoFillExpiredPrices();

        PurchaseOrderVO reloaded = reloadOrder(pending.getId());
        assertThat(reloaded.getItems().getFirst().getPricePending()).isTrue();
        assertThat(reloaded.getItems().getFirst().getPurchasePrice()).isNull();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM xsy_purchase_price_adjust WHERE purchase_order_item_id = ?",
            Integer.class, itemId)).isZero();
    }

    @Test
    @DisplayName("#5 自动回填只认同一采购单位的历史价：公斤价不会被填成斤价")
    void autoFillRequiresSamePurchaseUnit() {
        Long skuId = newOnShelfSku("PW5");
        Long supplierId = newPurchasableSupplier("PW5", skuId);
        PurchaseOrderVO history = submitOrder(createDraftOrder("PW5-H", supplierId, skuId, "2.0000", "6.0000").getId());
        PurchaseOrderVO pending = submitOrder(createPendingOrder("PW5-P", supplierId, skuId, "3.0000").getId());
        Long pendingItemId = pending.getItems().getFirst().getId();
        String pendingUnit = pending.getItems().getFirst().getPurchaseUnit();

        // 历史行改成另一个单位：没有同单位参考价时必须保持待定价，不能直接套用
        jdbc.update("UPDATE xsy_purchase_order_item SET purchase_unit_snapshot = ? WHERE id = ?",
            pendingUnit + "-OTHER", history.getItems().getFirst().getId());
        expireWindow(pending.getId(), pendingItemId);
        purchasePriceAutoFillJob.autoFillExpiredPrices();
        assertThat(reloadOrder(pending.getId()).getItems().getFirst().getPricePending()).isTrue();
        assertThat(reloadOrder(pending.getId()).getItems().getFirst().getPurchasePrice()).isNull();

        // 单位一致后才回填
        jdbc.update("UPDATE xsy_purchase_order_item SET purchase_unit_snapshot = ? WHERE id = ?", pendingUnit,
            history.getItems().getFirst().getId());
        purchasePriceAutoFillJob.autoFillExpiredPrices();
        PurchaseOrderVO filled = reloadOrder(pending.getId());
        assertThat(filled.getItems().getFirst().getPricePending()).isFalse();
        assertThat(filled.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("6.0000");
    }

    @Test
    @DisplayName("#6 自动锁单任务先补价再锁单：锁单不会抢先把可回填的单锁死")
    void autoLockJobFillsPriceBeforeLocking() {
        Long skuId = newOnShelfSku("PW6");
        Long supplierId = newPurchasableSupplier("PW6", skuId);
        submitOrder(createDraftOrder("PW6-H", supplierId, skuId, "2.0000", "5.5000").getId());
        PurchaseOrderVO pending = submitOrder(createPendingOrder("PW6-P", supplierId, skuId, "3.0000").getId());
        Long pendingItemId = pending.getItems().getFirst().getId();
        expireWindow(pending.getId(), pendingItemId);

        // 只跑锁单任务：补价必须在同一轮里先发生，否则这张单会被锁成待定价
        purchaseOrderAutoLockJob.lockExpiredOrders();

        PurchaseOrderVO reloaded = reloadOrder(pending.getId());
        assertThat(reloaded.getLockedFlag()).isTrue();
        assertThat(reloaded.getItems().getFirst().getPricePending()).isFalse();
        assertThat(reloaded.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("5.5000");
    }

    private void expireWindow(Long orderId, Long itemId) {
        jdbc.update("UPDATE xsy_purchase_order_item SET price_window_started_at = CURRENT_TIMESTAMP - INTERVAL '8 days' "
            + "WHERE id = ?", itemId);
        jdbc.update("UPDATE xsy_purchase_order SET auto_lock_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?",
            orderId);
        evictMybatisCache();
    }

    private PurchaseOrderVO createPendingOrder(String suffix, Long supplierId, Long skuId, String quantity) {
        PurchaseOrderAddForm form = orderForm(supplierId, fixtureWarehouseId(), skuId, quantity, null);
        form.getItems().getFirst().setPricePending(true);
        return purchaseOrderService.create(form, prefix + ":pending:" + suffix);
    }
}
