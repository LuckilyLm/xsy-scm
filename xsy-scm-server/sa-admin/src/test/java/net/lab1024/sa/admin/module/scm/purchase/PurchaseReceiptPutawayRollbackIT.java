package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入库确认失败时的**真实回滚**（B1，PG IT，无外层事务）。
 *
 * <p>与 W6 的 {@code ScmInventoryRollbackIT} 同理：要断言「异单位 putaway 失败后零残留」，
 * 必须关掉测试事务（{@code Propagation.NOT_SUPPORTED}），让每次 Service 调用自己开事务、
 * 自己提交或回滚，否则看到的只是「失败前写下的行还在」。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("入库确认失败的真实回滚（PG IT，无外层事务）")
class PurchaseReceiptPutawayRollbackIT extends ScmW6PgITBase {

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private PurchaseReceiptVO warehouseConfirmReceipt(Long orderId) {
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
        form.setRemark("B1 rollback 收货单");
        return purchaseReceiptService.create(form, prefix + ":rb:receipt:" + orderId);
    }

    private void putaway(Long receiptId) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
        form.setId(receiptId);
        form.setVersion(current.getVersion());
        purchaseReceiptService.putaway(form, prefix + ":rb:putaway:" + receiptId + ":" + UUID.randomUUID());
    }

    @Test
    @DisplayName("异单位 putaway 41001：putaway 状态、余额、流水、幂等记录零残留")
    void unitMismatchRollsBackTheWholePutaway() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("RB1");
        // 第一笔：以 kg 建立余额（kg）
        W6Fixture first = freeInboundFixture("RB1a", skuId, "5.0000", "kg");
        confirmReceipt(first.receipt().getId(), "5.0000");
        assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");

        // 第二笔：同 SKU 以「箱」为采购单位的 WAREHOUSE_CONFIRM 收货，确认后 putaway → 41001
        Long supplierId = newSupplier("RB1b");
        linkSupplierSku(supplierId, skuId, "box");
        PurchaseOrderVO order = createDraftOrder("RB1b", supplierId, skuId, "3.0000", "6.2000");
        submitOrder(order.getId());
        PurchaseReceiptVO second = warehouseConfirmReceipt(order.getId());
        confirmReceipt(second.getId(), "3.0000");
        assertThat(reloadReceipt(second.getId()).getPutawayStatus()).isEqualTo("PENDING");

        expectCode(() -> putaway(second.getId()), 41001);

        // putaway 状态未变、余额/流水不变
        assertThat(reloadReceipt(second.getId()).getPutawayStatus()).isEqualTo("PENDING");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

        // 清理：NOT_SUPPORTED 把数据提交到开发库。WAREHOUSE_CONFIRM + PENDING 的收货单是 B1 才有的
        // 合法形态（CONFIRMED 却没有流水），会打破 ScmInventoryBackfillIT 等对「CONFIRMED ⇒ 有流水」
        // 的 V19 全局对账假设，因此物理删除这张 box 收货单，避免残留污染其它测试。
        jdbc.update("DELETE FROM receipt_weighing_record WHERE purchase_receipt_item_id IN "
                + "(SELECT id FROM purchase_receipt_item WHERE purchase_receipt_id = ?)", second.getId());
        jdbc.update("DELETE FROM purchase_receipt_item WHERE purchase_receipt_id = ?", second.getId());
        jdbc.update("DELETE FROM purchase_receipt WHERE id = ?", second.getId());
    }
}
