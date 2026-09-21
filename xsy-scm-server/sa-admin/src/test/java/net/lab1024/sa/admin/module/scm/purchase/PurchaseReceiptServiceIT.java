package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOperationLogVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 收货单生命周期（W5 Target Design §11.2，6 例）。
 *
 * <p>收货单的三条硬规则：
 * <ol>
 *   <li><b>行由服务端生成</b>：`create` 按采购单的**全部活动行**建行，调用方不能挑行 ——
 *       否则「确认时必须覆盖全部行」（40998）就失去基准集合；</li>
 *   <li><b>只有 SUBMITTED / PARTIALLY_RECEIVED 的采购单可收货</b>：DRAFT / RECEIVED /
 *       SHORT_CLOSED / CANCELLED 一律 40991；</li>
 *   <li><b>`update` 只允许改备注</b>：草稿态也不允许改数量 —— 数量只在 `confirm` 一次性落库，
 *       这样「草稿数量」与「实际收货」不会出现两套真相。</li>
 * </ol>
 *
 * <p>新建的收货行对账量必须是**初始态**（`remaining = planned`、`difference = −planned`），
 * 这不是「零值」而是 P24 恒等式在 `cumulative = 0` 时的取值 —— 断言它同时锁住了初始口径。
 */
@DisplayName("收货单生命周期：create / update / delete / 幂等（PG IT）")
class PurchaseReceiptServiceIT extends ScmW5PgITBase {

    /**
     * 用指定数量确认收货（自动回读当前版本）。
     */
    private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), quantity)),
                prefix + ":confirm:" + current.getId() + ":" + quantity);
    }

    // ------------------------------------------------------------------
    // 1. create
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：按采购单活动行生成收货行，单号 PR+日期+序号，对账量取初始态")
    void createGeneratesReceiptForEveryActiveOrderItem() {
        ReceiptFixture fx = receiptFixture("RC1", "10.0000");
        PurchaseReceiptVO receipt = fx.receipt();

        assertThat(receipt.getReceiptNo()).matches("^PR\\d{14,}$");
        assertThat(receipt.getStatus()).isEqualTo("DRAFT");
        assertThat(receipt.getPurchaseOrderId()).isEqualTo(fx.order().getId());
        assertThat(receipt.getPurchaseOrderNo()).isEqualTo(fx.order().getOrderNo());
        assertThat(receipt.getSupplierId()).isEqualTo(fx.supplierId());
        assertThat(receipt.getWarehouseId()).isEqualTo(seedWarehouseId());
        // 草稿态没有收货时间与操作者
        assertThat(receipt.getReceivedAt()).isNull();
        assertThat(receipt.getConfirmedAt()).isNull();
        assertThat(receipt.getOperator()).isNull();

        assertThat(receipt.getItems()).hasSize(1);
        PurchaseReceiptItemVO line = receipt.getItems().getFirst();
        assertThat(line.getPurchaseOrderItemId()).isEqualTo(fx.orderItemId());
        assertThat(line.getSkuId()).isEqualTo(fx.skuId());
        assertThat(line.getProductType()).isEqualTo("NON_STANDARD");
        assertThat(line.getPurchaseUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(line.getPlannedQuantity()).isEqualByComparingTo("10.0000");
        // P24 在 cumulative = 0 时的取值
        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getRemainingQuantity()).isEqualByComparingTo("10.0000");
        assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getReceiptDifference()).isEqualByComparingTo("-10.0000");
        assertThat(line.getActualWeight()).isNull();
        assertThat(line.getWeighingSource()).isNull();

        // RECEIPT_CREATE 日志：Q14 的第四分支，两个 id 都非空
        // 日志按 `created_at DESC, id DESC` 返回（与 W4 的 order_operation_log 一致）→ **最新在前**
        List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(fx.order().getId());
        assertThat(logs).extracting(PurchaseOperationLogVO::getOperationType)
                .containsExactly("RECEIPT_CREATE", "SUBMIT", "CREATE");
        PurchaseOperationLogVO log = logs.getFirst();
        assertThat(log.getPurchaseOrderId()).isEqualTo(fx.order().getId());
        assertThat(log.getPurchaseReceiptId()).isEqualTo(receipt.getId());
        assertThat(log.getAfterData()).containsEntry("receiptNo", receipt.getReceiptNo());
    }

    // ------------------------------------------------------------------
    // 2 / 3. create 的前置校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：草稿采购单 → 40991；采购单不存在 → 40481")
    void createRejectsDraftOrderAndUnknownOrder() {
        Long skuId = newOnShelfSku("RC2");
        Long supplierId = newPurchasableSupplier("RC2", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO draft = createDraftOrder("RC2", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        // DRAFT 不可收货（T8）
        expectCode(() -> createReceipt(draft.getId()), 40991);

        expectCode(() -> createReceipt(999_999_999L), 40481);

        // 提交之后同一张单就能建收货单了 —— 证明上一条拒绝的原因是状态而不是别的。
        //
        // 必须换一个幂等键：上面那次失败调用已经**在本用例的事务里**插入了 claim 行
        // （`result_data` 为 null，因为业务在写结果之前就抛了）。同键再来一次会命中
        // 「已提交的幂等记录缺少 result_data」。真实环境里失败的调用整事务回滚，
        // claim 行不会残留 —— 这是「整个用例共用一个事务」的测试基建特性，不是产品缺陷。
        submitOrder(draft.getId());
        assertThat(createAnotherReceipt(draft.getId(), "after-submit").getStatus()).isEqualTo("DRAFT");
    }

    // ------------------------------------------------------------------
    // 4. update 只改备注
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update：只允许改备注；确认之后 → 40988")
    void updateOnlyAllowsRemarkOnDraft() {
        ReceiptFixture fx = receiptFixture("RC4", "10.0000");

        PurchaseReceiptVO updated = purchaseReceiptService.update(receiptRemarkForm(
                fx.receipt().getId(), fx.receipt().getVersion(), "第一次到货"));
        assertThat(updated.getRemark()).isEqualTo("第一次到货");
        assertThat(updated.getVersion()).isGreaterThan(fx.receipt().getVersion());
        assertThat(purchaseQueryService.orderLogs(fx.order().getId()))
                .extracting(PurchaseOperationLogVO::getOperationType)
                .contains("RECEIPT_UPDATE");

        // 版本过期同样被拦
        expectCode(() -> purchaseReceiptService.update(receiptRemarkForm(
                fx.receipt().getId(), fx.receipt().getVersion(), "过期版本")), 40921);

        // 确认之后不可再改
        PurchaseReceiptVO confirmed = confirm(fx, "10.0000");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        expectCode(() -> purchaseReceiptService.update(receiptRemarkForm(
                confirmed.getId(), confirmed.getVersion(), "改不了")), 40988);
    }

    // ------------------------------------------------------------------
    // 5. delete
    // ------------------------------------------------------------------

    @Test
    @DisplayName("delete：仅草稿可删（确认后 → 40994），删除本身幂等")
    void deleteOnlyAllowsDraftAndIsIdempotent() {
        ReceiptFixture draftFixture = receiptFixture("RC5", "10.0000");
        Long draftId = draftFixture.receipt().getId();

        purchaseReceiptService.delete(receiptDeleteForm(draftId));
        expectCode(() -> reloadReceipt(draftId), 40483);
        // 幂等：已删视为成功
        purchaseReceiptService.delete(receiptDeleteForm(draftId));

        // 确认后不可删
        ReceiptFixture confirmedFixture = receiptFixture("RC5b", "10.0000");
        PurchaseReceiptVO confirmed = confirm(confirmedFixture, "10.0000");
        expectCode(() -> purchaseReceiptService.delete(receiptDeleteForm(confirmed.getId())), 40994);
        assertThat(reloadReceipt(confirmed.getId()).getStatus()).isEqualTo("CONFIRMED");
    }

    // ------------------------------------------------------------------
    // 6. create 幂等
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create 幂等：同键同内容重放，不产生第二张收货单")
    void createIsIdempotentWithinOrderScope() {
        ReceiptFixture fx = receiptFixture("RC6", "10.0000");

        // 与 createReceipt 完全相同的请求（同 key、同 remark、同 receiptMode）
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(fx.order().getId());
        form.setReceiptMode(ScmReceiptModeEnum.DIRECT.name());
        form.setRemark("W5 IT 收货单");
        PurchaseReceiptVO replayed = purchaseReceiptService.create(
                form, prefix + ":receipt:" + fx.order().getId());

        assertThat(replayed.getId()).isEqualTo(fx.receipt().getId());
        assertThat(replayed.getReceiptNo()).isEqualTo(fx.receipt().getReceiptNo());
        assertThat(receiptOfOrder(fx.order().getId()).getId()).isEqualTo(fx.receipt().getId());
        // 重放不写第二条 RECEIPT_CREATE 日志（同样按 DESC 返回）
        assertThat(purchaseQueryService.orderLogs(fx.order().getId()))
                .extracting(PurchaseOperationLogVO::getOperationType)
                .containsExactly("RECEIPT_CREATE", "SUBMIT", "CREATE");
    }
}
