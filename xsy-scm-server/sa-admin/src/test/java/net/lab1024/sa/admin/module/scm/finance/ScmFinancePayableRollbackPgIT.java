package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.finance.service.FinancePayableService;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 应付生成失败时收货确认**真实回滚**（F1-2A，PG IT，无外层事务）。
 *
 * <p>要断言「财务写失败后收货单没留下 CONFIRMED」，每次 Service 调用必须自己提交或自己回滚；
 * 若沿用 {@link ScmW5PgITBase} 的「整包在一个事务里回滚」形态，失败前写下的行还在同一个事务中，
 * 看到的一直是「什么都没发生」——那是测试夹具造成的假绿，不是回滚的证据。
 *
 * <p><b>怎么让财务写入失败</b>：不去改代码或造一个假的 Bean，而是先占掉
 * {@code uk_finance_payable_item_source_active} 的来源键。这样失败发生在数据库层，
 * 与真实故障（约束、触发器、超时）走的是同一条传播路径：DAO 抛 → 生成器抛 →
 * {@code confirm} 的同一事务整体回滚。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("应付生成失败与收货确认回滚（F1-2A，PG IT，无外层事务）")
class ScmFinancePayableRollbackPgIT extends ScmW6PgITBase {

    @Autowired
    private FinancePayableService financePayableService;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private PurchaseReceiptVO draftDirectReceipt(String suffix, Long skuId, String planned, String price) {
        Long supplierId = newSupplier(suffix);
        linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, planned, price);
        submitOrder(order.getId());

        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(order.getId());
        form.setReceiptMode(ScmReceiptModeEnum.DIRECT.name());
        form.setRemark("F1-2A rollback 收货单");
        return purchaseReceiptService.create(form, prefix + ":receipt:" + order.getId());
    }

    private void confirm(PurchaseReceiptVO receipt, String quantity) {
        PurchaseReceiptVO current = reloadReceipt(receipt.getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), quantity)),
                prefix + ":confirm:" + current.getId() + ":" + UUID.randomUUID());
    }

    // ------------------------------------------------------------------
    // 1. 财务写入失败 → 收货确认整笔回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("来源键被占：confirm 抛错 → 收货单仍 DRAFT、无应付无日志、库存与幂等零残留")
    void financeFailureRollsBackTheWholeReceiptConfirm() {
        Long skuId = newOnShelfSku("FPR1");
        PurchaseReceiptVO receipt = draftDirectReceipt("FPR1", skuId, "8.0000", "6.2000");
        Long receiptItemId = receipt.getItems().getFirst().getId();

        // 先占掉这一行的应付来源键（模拟「同一收货行已被别的应付单记账」的数据异常）
        jdbc.update("INSERT INTO finance_payable_item (payable_id, source_type, source_id, "
                        + "purchase_order_item_id, sku_id, sku_name_snapshot, unit_snapshot, "
                        + "quantity, unit_price, amount, created_at, updated_at) "
                        + "VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, ?, ?, '占位', 'kg', "
                        + "        1.0000, 1.0000, 1.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                -1L, receiptItemId, receipt.getItems().getFirst().getPurchaseOrderItemId(), skuId);

        try {
            assertThatThrownBy(() -> confirm(receipt, "8.0000"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("收货行已挂在别的应付单上");

            // 采购侧什么都没留下：状态、累计量、单号、幂等结果全部回到确认前
            PurchaseReceiptVO after = reloadReceipt(receipt.getId());
            assertThat(after.getStatus()).isEqualTo("DRAFT");
            assertThat(after.getConfirmedAt()).isNull();
            assertThat(after.getPutawayStatus()).isEqualTo("PENDING");
            assertThat(reloadOrder(receipt.getPurchaseOrderId()).getStatus()).isEqualTo("SUBMITTED");
            assertThat(reloadOrder(receipt.getPurchaseOrderId()).getItems().getFirst().getReceivedQuantity())
                    .isEqualByComparingTo("0.0000");

            // 财务侧既没有单头也没有第二行明细，也没有「改了很多但没记下来」的日志
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_payable WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?",
                    Integer.class, receipt.getId())).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_payable_item WHERE source_type = 'PURCHASE_RECEIPT_ITEM' "
                            + "AND source_id = ? AND payable_id > 0", Integer.class, receiptItemId)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_operation_log l WHERE l.business_type = 'PAYABLE' "
                            + "AND l.business_id IN (SELECT id FROM finance_payable "
                            + "                   WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?)",
                    Integer.class, receipt.getId()))
                    .as("应付未成立 → 不留生成日志").isZero();

            // DIRECT 收货确认的库存写入也在同一事务里，因此同样零残留
            assertThat(movementCount(seedWarehouseId(), skuId)).isZero();
            assertThat(balanceRowCount(seedWarehouseId(), skuId)).isZero();
        } finally {
            // 占位行是本用例故意造的假数据：append-only 表在库里没有删除入口，测试负责清干净
            jdbc.update("DELETE FROM finance_payable_item WHERE payable_id = -1");
        }
    }

    // ------------------------------------------------------------------
    // 2. 生成器不吃「自己开一个事务」这条路
    // ------------------------------------------------------------------

    @Test
    @DisplayName("无外层事务调用生成器：MANDATORY 直接拒绝，绝不会自己提交一张应付")
    void generatorRefusesToRunWithoutTheTriggerTransaction() {
        assertThatThrownBy(() -> financePayableService.generateOnReceiptConfirm(-1L))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_payable WHERE source_id = -1", Integer.class)).isZero();
    }
}
