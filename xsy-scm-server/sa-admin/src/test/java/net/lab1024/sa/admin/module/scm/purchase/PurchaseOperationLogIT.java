package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderCancelForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOperationLogVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购操作日志（W5 Target Design §7.12 / Q14，5 例）。
 *
 * <p><b>Q14 的核心是「归属由操作类型决定」</b>，不是一个宽松的非空约束：
 * <pre>
 * DEMAND_GENERATE   purchase_order_id = NULL  AND purchase_receipt_id = NULL   ← 需求还没有单据
 * DEMAND_ALLOCATE   purchase_order_id ≠ NULL  AND purchase_receipt_id = NULL   ← 由 itemId 反查得到
 * CREATE/UPDATE/SUBMIT/CANCEL/SHORT_CLOSE/DELETE   purchase_order_id ≠ NULL
 * RECEIPT_CREATE/RECEIPT_UPDATE/RECEIPT_CONFIRM   两者都 ≠ NULL
 * </pre>
 * 写成 `IS NOT NULL OR IS NOT NULL` 会让 `DEMAND_GENERATE` 无法落库（两个都空），
 * 也会让「收货日志漏填采购单」这种错误溜过去 —— V15 的
 * `ck_purchase_operation_log_owner` 是**四分支** CHECK，本类逐分支验证。
 *
 * <p><b>日志是只追加的审计事实</b>：没有 `version` / `updated_*`，
 * 单据的后续变更不得改写既有行。因此「跑完整流程后回看早期日志仍原样」也是断言的一部分。
 */
@DisplayName("采购操作日志：Q14 归属四分支 + 快照契约（PG IT）")
class PurchaseOperationLogIT extends ScmW5PgITBase {

    private Map<String, Object> latestLog(String operationType) {
        return jdbc.queryForMap(
                "SELECT purchase_order_id, purchase_receipt_id, operation_type, operator, reason, "
                        + "before_data, after_data FROM purchase_operation_log "
                        + "WHERE operation_type = ? ORDER BY id DESC LIMIT 1", operationType);
    }

    private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), quantity)),
                prefix + ":log:" + current.getId() + ":" + quantity);
    }

    // ------------------------------------------------------------------
    // 1. DEMAND_GENERATE：两个归属都为空
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q14 分支一：DEMAND_GENERATE 的两个归属 id 同时为空（无前态快照）")
    void demandGenerateLogHasBothOwnerIdsNull() {
        Long skuId = newOnShelfSku("LG1");
        Long supplierId = newPurchasableSupplier("LG1", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        generateDemandFor(supplierId, salesOrder);

        Map<String, Object> log = latestLog("DEMAND_GENERATE");
        assertThat(log.get("purchase_order_id")).isNull();
        assertThat(log.get("purchase_receipt_id")).isNull();
        // 操作者落的是 `userType:employeeId`（ScmOperator 的口径），不是员工姓名
        assertThat(log.get("operator")).isEqualTo(ScmOperator.current());
        assertThat(log.get("before_data")).isNull();
        assertThat(String.valueOf(log.get("after_data"))).contains("createdCount");
    }

    // ------------------------------------------------------------------
    // 2. DEMAND_ALLOCATE：反查得到采购单 id
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q14 分支二：DEMAND_ALLOCATE 通过 purchaseOrderItemId 反查出 purchase_order_id")
    void demandAllocateLogResolvesPurchaseOrderId() {
        Long skuId = newOnShelfSku("LG2");
        Long supplierId = newPurchasableSupplier("LG2", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        // 采购单行先建出来（**不带**分配），再由独立分配端点补上 —— 这条路径才会写 DEMAND_ALLOCATE
        PurchaseOrderVO order = createDraftOrder("LG2", supplierId, skuId, "3.0000", "6.2000");
        Long orderItemId = order.getItems().getFirst().getId();
        assertThat(order.getItems().getFirst().getAllocations()).isEmpty();

        // `allocate` 只接受 SUBMITTED 的采购单：DRAFT 还没定稿，终态已结束（40982）
        submitOrder(order.getId());

        PurchaseDemandAllocateForm form = new PurchaseDemandAllocateForm();
        form.setDemandId(demand.getId());
        form.setPurchaseOrderItemId(orderItemId);
        form.setQuantity("3.0000");
        form.setSupplierId(supplierId);
        form.setWarehouseId(seedWarehouseId());
        form.setVersion(reloadDemand(demand.getId()).getVersion());
        purchaseDemandService.allocate(form, prefix + ":LG2:alloc");

        Map<String, Object> log = latestLog("DEMAND_ALLOCATE");
        assertThat(log.get("purchase_order_id")).isEqualTo(order.getId());
        assertThat(log.get("purchase_receipt_id")).isNull();
        // 分配确实落地了，而不只是写了条日志
        assertThat(allocationOf(orderItemId, demand.getId()).getAllocatedQuantity())
                .isEqualByComparingTo("3.0000");
    }

    // ------------------------------------------------------------------
    // 3. RECEIPT_*：两个归属都非空 + 只追加
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q14 分支四：RECEIPT_CREATE / RECEIPT_CONFIRM 两个归属都非空，且早期日志不被改写")
    void receiptLogsCarryBothOwnerIdsAndAreAppendOnly() {
        ReceiptFixture fx = receiptFixture("LG3", "10.0000");
        // 记下 RECEIPT_CREATE 的原始快照，确认之后回看必须一字不变
        Map<String, Object> createLogBefore = latestLog("RECEIPT_CREATE");

        PurchaseReceiptVO confirmed = confirm(fx, "10.0000");

        Map<String, Object> createLog = latestLog("RECEIPT_CREATE");
        assertThat(createLog.get("purchase_order_id")).isEqualTo(fx.order().getId());
        assertThat(createLog.get("purchase_receipt_id")).isEqualTo(fx.receipt().getId());
        assertThat(createLog.get("after_data")).isEqualTo(createLogBefore.get("after_data"));

        Map<String, Object> confirmLog = latestLog("RECEIPT_CONFIRM");
        assertThat(confirmLog.get("purchase_order_id")).isEqualTo(fx.order().getId());
        assertThat(confirmLog.get("purchase_receipt_id")).isEqualTo(confirmed.getId());
        // 确认日志的 after_data 带上本次单据的新状态
        assertThat(String.valueOf(confirmLog.get("after_data"))).contains("RECEIVED");
    }

    // ------------------------------------------------------------------
    // 4. 单据变更日志的快照形状
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CREATE 无前态、UPDATE / SUBMIT 有前态与后态，且状态可读")
    void orderMutationLogsExposeStatusSnapshots() {
        Long skuId = newOnShelfSku("LG4");
        Long supplierId = newPurchasableSupplier("LG4", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO order = createDraftOrder("LG4", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(order.getId());
        assertThat(logs).hasSize(1);
        PurchaseOperationLogVO create = logs.getFirst();
        assertThat(create.getOperationType()).isEqualTo("CREATE");
        assertThat(create.getBeforeData()).isNull();
        assertThat(create.getAfterData()).containsEntry("status", "DRAFT");

        // UPDATE：前后态都有，且能看出金额变化
        purchaseOrderService.update(editForm(order.getId(), "2.0000", "5.0000",
                allocation(reloadDemand(demand.getId()), "2.0000")));

        // SUBMIT：前态 DRAFT、后态 SUBMITTED
        submitOrder(order.getId());

        logs = purchaseQueryService.orderLogs(order.getId());
        assertThat(logs).extracting(PurchaseOperationLogVO::getOperationType)
                .containsExactly("SUBMIT", "UPDATE", "CREATE");

        // DESC → 索引 0 = SUBMIT、1 = UPDATE、2 = CREATE
        PurchaseOperationLogVO update = logs.get(1);
        assertThat(update.getBeforeData()).containsEntry("totalAmount", "18.6000");
        assertThat(update.getAfterData()).containsEntry("totalAmount", "10.0000");
        assertThat(update.getBeforeData()).containsKeys("items", "status", "version");
        assertThat(update.getAfterData()).containsKeys("items", "status", "version");

        PurchaseOperationLogVO submit = logs.getFirst();
        assertThat(submit.getBeforeData()).containsEntry("status", "DRAFT");
        assertThat(submit.getAfterData()).containsEntry("status", "SUBMITTED");

        // 只追加：id 严格递减（DESC 排序下等价于「严格递增」，条数与动作数一致）
        assertThat(logs).extracting(PurchaseOperationLogVO::getId)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    // ------------------------------------------------------------------
    // 5. CANCEL 的 reason 独立于快照
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CANCEL：原因落在日志的 reason 列（不是只塞进 after_data）")
    void cancelLogCarriesReasonOutsideSnapshot() {
        Long skuId = newOnShelfSku("LG5");
        Long supplierId = newPurchasableSupplier("LG5", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO order = createDraftOrder("LG5", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        PurchaseOrderCancelForm cancel = new PurchaseOrderCancelForm();
        cancel.setId(order.getId());
        cancel.setVersion(order.getVersion());
        cancel.setCancelReason("客户取消订单");
        purchaseOrderService.cancel(cancel, prefix + ":LG5:cancel");

        Map<String, Object> log = latestLog("CANCEL");
        assertThat(log.get("purchase_order_id")).isEqualTo(order.getId());
        assertThat(log.get("purchase_receipt_id")).isNull();
        // reason 是独立列：审计查询不需要解析 JSONB
        assertThat(log.get("reason")).isEqualTo("客户取消订单");
        assertThat(String.valueOf(log.get("after_data"))).contains("CANCELLED");
    }
}
