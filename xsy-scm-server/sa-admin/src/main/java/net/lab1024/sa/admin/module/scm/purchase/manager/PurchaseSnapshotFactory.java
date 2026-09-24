package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderEntity;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseDemandStatusEnum;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOperationLogEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采购快照装配（W5 Target Design §5.11 快照矩阵）。
 *
 * <p>快照矩阵的四个冻结时点：
 * <table border="1">
 *   <tr><th>快照</th><th>落点</th><th>冻结时点</th></tr>
 *   <tr><td>Supplier</td><td>{@code purchase_order} / {@code purchase_receipt}</td><td>创建 / 编辑时（DRAFT 可刷新）；收货单创建时继承</td></tr>
 *   <tr><td>SKU</td><td>{@code purchase_order_item} / {@code purchase_receipt_item} / {@code purchase_demand}</td><td>创建 / 编辑时；收货单创建时继承；需求生成时</td></tr>
 *   <tr><td>Purchase unit</td><td>{@code purchase_order_item.purchase_unit_snapshot}</td><td>创建 / 编辑时，来源 {@code supplier_sku.purchase_unit}</td></tr>
 *   <tr><td>Purchase price</td><td>{@code purchase_order_item.purchase_price} + {@code line_amount}</td><td>创建 / 编辑时（人工确认值）</td></tr>
 * </table>
 *
 * <p>**`submit` 后永不回读主数据**（P4）；**收货单行不存价格**（A-D8）。
 */
public final class PurchaseSnapshotFactory {

    /**
     * 与 V15 的 `AT TIME ZONE 'Asia/Shanghai'` 字面量保持一致（Q6a）。
     */
    public static final ZoneId ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private PurchaseSnapshotFactory() {
    }

    /**
     * 采购单头快照。`orderNo` 由 {@code PurchaseNumberGenerator} 在事务内生成后回填。
     *
     * <p>供应商 / 仓库快照在创建 / 编辑时刷新（DRAFT 可刷新），`submit` 后不再回读。
     *
     * <p>{@code purchaserId} 必须是服务端裁决出的归属（见 {@code PurchaseOwnerResolver}），
     * 不能传 {@code form.getPurchaserId()}：表单值是一个客户端可任意填写的数字，
     * 而它同时是采购员数据范围的行归属依据。
     */
    public static PurchaseOrderEntity order(String supplierCode, String supplierName,
                                            String warehouseCode, String warehouseName,
                                            Long purchaserId, PurchaseOrderAddForm form) {
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setSupplierId(form.getSupplierId());
        order.setSupplierCodeSnapshot(supplierCode);
        order.setSupplierNameSnapshot(supplierName);
        order.setPurchaserId(purchaserId);
        order.setWarehouseId(form.getWarehouseId());
        order.setWarehouseCodeSnapshot(warehouseCode);
        order.setWarehouseNameSnapshot(warehouseName);
        order.setPlannedArrivalDate(form.getPlannedArrivalDate());
        order.setRemark(PurchaseOrderValidator.trim(form.getRemark()));
        order.setStatus("DRAFT");
        order.setVersion(0);
        order.setDeleted(false);
        return order;
    }

    /**
     * 采购单行快照。
     *
     * <p><b>Q17</b>：`purchaseUnitSnapshot` 来自 `supplier_sku.purchase_unit`，
     * **不是** `product_sku.sale_unit` —— 需求单位（销售单位）与采购单位是两个独立快照，
     * 生成时冻结、永不互相覆盖。
     *
     * <p>`purchasePrice` 取表单值（人工确认值）；`supplier_sku.reference_price` 只作**建议值**，
     * **不自动写入**（K7）。
     */
    public static PurchaseOrderItemEntity item(PurchaseOrderAddForm.Item form,
                                               ProductSkuOptionVO sku,
                                               String spuCode,
                                               SupplierSkuEntity supplierSku) {
        PurchaseOrderItemEntity item = new PurchaseOrderItemEntity();
        item.setId(form.getId());
        item.setVersion(form.getVersion());
        item.setSkuId(sku.getSkuId());
        item.setSpuId(sku.getSpuId());
        item.setSpuCodeSnapshot(spuCode);
        item.setProductNameSnapshot(sku.getProductName());
        item.setSkuCodeSnapshot(sku.getSkuCode());
        // 与 W2 一致：名称取 SPU 名称而不是 SKU 规格名（legacy 不变量 R4）
        item.setSkuNameSnapshot(sku.getProductName());
        item.setSpecValuesSnapshot(copySpecValues(sku.getSpecValues()));
        item.setPurchaseUnitSnapshot(supplierSku.getPurchaseUnit());
        item.setProductTypeSnapshot(sku.getProductType());
        item.setPlannedQuantity(PurchaseOrderValidator.decimal(form.getQuantity(), true));
        item.setReceivedQuantity(zero());
        item.setPurchasePrice(PurchaseOrderValidator.decimal(form.getPrice(), false));
        item.setLineAmount(PurchaseAmountCalculator.lineAmount(
                item.getPlannedQuantity(), item.getPurchasePrice()));
        item.setDeleted(false);
        return item;
    }

    /**
     * 收货单头快照：供应商 / 仓库 / 采购单号从采购单**继承**。
     */
    public static PurchaseReceiptEntity receipt(PurchaseOrderEntity order, String receiptNo, String remark) {
        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setReceiptNo(receiptNo);
        receipt.setPurchaseOrderId(order.getId());
        receipt.setPurchaseOrderNoSnapshot(order.getOrderNo());
        receipt.setSupplierId(order.getSupplierId());
        receipt.setSupplierCodeSnapshot(order.getSupplierCodeSnapshot());
        receipt.setSupplierNameSnapshot(order.getSupplierNameSnapshot());
        receipt.setWarehouseId(order.getWarehouseId());
        receipt.setWarehouseCodeSnapshot(order.getWarehouseCodeSnapshot());
        receipt.setWarehouseNameSnapshot(order.getWarehouseNameSnapshot());
        receipt.setStatus("DRAFT");
        receipt.setRemark(PurchaseOrderValidator.trim(remark));
        receipt.setVersion(0);
        receipt.setDeleted(false);
        return receipt;
    }

    /**
     * 收货单行快照：商品 / 单位 / 计划量从采购行**继承**，5 个对账数量从「尚未收货」起算。
     *
     * <pre>
     * received = cumulative = over = 0
     * remaining = planned
     * difference = 0 − planned = −planned      （与 ck_purchase_receipt_item_reconciliation 一致）
     * </pre>
     *
     * <p>**不复制价格**：收货只记数量 / 重量（A-D8）。
     */
    public static PurchaseReceiptItemEntity receiptItem(PurchaseReceiptEntity receipt,
                                                        PurchaseOrderItemEntity orderItem,
                                                        int sortOrder) {
        PurchaseReceiptItemEntity item = new PurchaseReceiptItemEntity();
        item.setPurchaseReceiptId(receipt.getId());
        item.setPurchaseOrderItemId(orderItem.getId());
        item.setSkuId(orderItem.getSkuId());
        item.setSpuCodeSnapshot(orderItem.getSpuCodeSnapshot());
        item.setProductNameSnapshot(orderItem.getProductNameSnapshot());
        item.setSkuCodeSnapshot(orderItem.getSkuCodeSnapshot());
        item.setSkuNameSnapshot(orderItem.getSkuNameSnapshot());
        item.setSpecValuesSnapshot(copySpecValues(orderItem.getSpecValuesSnapshot()));
        item.setPurchaseUnitSnapshot(orderItem.getPurchaseUnitSnapshot());
        item.setProductTypeSnapshot(orderItem.getProductTypeSnapshot());
        item.setPlannedQuantity(orderItem.getPlannedQuantity());
        item.setReceivedQuantity(zero());
        item.setCumulativeReceivedQuantity(zero());
        item.setRemainingQuantity(orderItem.getPlannedQuantity());
        item.setOverReceiptQuantity(zero());
        item.setReceiptDifference(orderItem.getPlannedQuantity().negate());
        item.setSortOrder(sortOrder);
        item.setVersion(0);
        item.setDeleted(false);
        return item;
    }

    /**
     * Q6a：`demand_date` = `source_confirmed_at` 在 **Asia/Shanghai** 下的日期。
     *
     * <p>**禁止**用汇总窗口的第一天（`date(startAt)`）—— 跨多日窗口会把全部需求压平成同一天。
     * 结果由 V15 的 `ck_purchase_demand_date` 在 DB 层复核，两侧口径必须一致。
     */
    public static LocalDate demandDate(OffsetDateTime sourceConfirmedAt) {
        return sourceConfirmedAt.atZoneSameInstant(ASIA_SHANGHAI).toLocalDate();
    }

    /**
     * 需求快照装配（`generate` 用）。
     *
     * <p><b>Q17</b>：`demandUnitSnapshot` 取 `sales_order_item.sale_unit_snapshot`（**销售单位**），
     * 生成时冻结、**永不改写**。它**不是**采购单位 —— 采购单位来自 `supplier_sku.purchase_unit`
     * 并落在 `purchase_order_item.purchase_unit_snapshot`，两者在分配时做一致性校验。
     *
     * <p>`requiredQuantity` 取 **`actual_quantity`（实数量）** 而不是 `ordered_quantity`，
     * 与 A 源口径一致（§7.4 订单集成表）。
     *
     * <p>`status` 固定从 `PENDING` 起（V15 的默认值也一致）；`allocatedQuantity` 从 0 起。
     */
    public static PurchaseDemandEntity demand(SalesOrderItemEntity source,
                                              SalesOrderEntity order,
                                              Long supplierId,
                                              Long warehouseId,
                                              Long purchaserId) {
        PurchaseDemandEntity demand = new PurchaseDemandEntity();
        demand.setSalesOrderId(order.getId());
        demand.setSalesOrderItemId(source.getId());
        demand.setSpuId(source.getSpuId());
        demand.setSkuId(source.getSkuId());
        demand.setSalesOrderNoSnapshot(order.getOrderNo());
        demand.setSpuCodeSnapshot(source.getSpuCodeSnapshot());
        demand.setProductNameSnapshot(source.getProductNameSnapshot());
        demand.setSkuCodeSnapshot(source.getSkuCodeSnapshot());
        // 与 W2 / W4 一致：SKU 名称取 SPU 名称（legacy 不变量 R4）
        demand.setSkuNameSnapshot(source.getProductNameSnapshot());
        demand.setSpecValuesSnapshot(copySpecValues(source.getSpecValuesSnapshot()));
        demand.setDemandUnitSnapshot(source.getSaleUnitSnapshot());       // Q17
        demand.setProductTypeSnapshot(source.getProductTypeSnapshot());
        demand.setRequiredQuantity(source.getActualQuantity());
        demand.setAllocatedQuantity(zero());
        demand.setSupplierId(supplierId);
        demand.setWarehouseId(warehouseId);
        demand.setPurchaserId(purchaserId);
        demand.setStatus(ScmPurchaseDemandStatusEnum.PENDING.name());
        demand.setSourceConfirmedAt(order.getConfirmedAt());
        demand.setDemandDate(demandDate(order.getConfirmedAt()));          // Q6a
        demand.setVersion(0);
        demand.setDeleted(false);
        return demand;
    }

    /**
     * 分配行上的 `demand_snapshot`（§5.4，A 源只有 `{demandId}`）。
     *
     * <p>作用：让每条 allocation **自带证据**，即使需求行之后被改/被删，
     * 也能从分配行回答「当初挂的是哪张订单行、什么单位、多少量」。
     * 数量按 §7.2 的 4 位定点**字符串**存（不是 JSON 数字）—— 避免 JSON 数字的
     * 浮点语义污染定点纪律。
     */
    public static Map<String, Object> allocationDemandSnapshot(PurchaseDemandEntity demand) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("demandId", demand.getId());
        snapshot.put("salesOrderId", demand.getSalesOrderId());
        snapshot.put("salesOrderItemId", demand.getSalesOrderItemId());
        snapshot.put("skuId", demand.getSkuId());
        snapshot.put("requiredQuantity", fixed(demand.getRequiredQuantity()));
        snapshot.put("demandUnit", demand.getDemandUnitSnapshot());
        snapshot.put("skuCode", demand.getSkuCodeSnapshot());
        snapshot.put("skuName", demand.getSkuNameSnapshot());
        return snapshot;
    }

    /**
     * 4 位定点字符串；`null` 保持 `null`（不写成 `"0.0000"`）。
     */
    public static String fixed(BigDecimal value) {
        return value == null ? null : value.setScale(PurchaseAmountCalculator.SCALE, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * 操作日志行（§7.12）。
     *
     * <p><b>Q14 的归属由调用方决定，本方法不做推断</b>：
     * `DEMAND_GENERATE` 传两个 `null`；`DEMAND_ALLOCATE` 传反查得到的 `purchaseOrderId` + `null`；
     * `RECEIPT_*` 传两个非空。DB 的 `ck_purchase_operation_log_owner` 会复核 ——
     * 传错就写不进去，这是**故意**的（让错误在写入点暴露，而不是留一条脏日志）。
     *
     * @param before 变更前快照；`null` 表示「无前态」（如 `CREATE` / `DEMAND_GENERATE`）
     * @param after  变更后快照
     */
    public static PurchaseOperationLogEntity operationLog(ScmPurchaseOperationTypeEnum type,
                                                          Long purchaseOrderId,
                                                          Long purchaseReceiptId,
                                                          String reason,
                                                          Map<String, Object> before,
                                                          Map<String, Object> after) {
        PurchaseOperationLogEntity log = new PurchaseOperationLogEntity();
        log.setOperationType(type.name());
        log.setPurchaseOrderId(purchaseOrderId);
        log.setPurchaseReceiptId(purchaseReceiptId);
        log.setOperator(ScmOperator.current());
        log.setReason(PurchaseOrderValidator.trim(reason));
        log.setBeforeData(before);
        log.setAfterData(after);
        log.setCreatedBy(ScmOperator.current());
        return log;
    }

    /**
     * 可变的 JSONB 快照容器（`Map.of` 不可变，日志快照需要逐项 put）。
     */
    public static Map<String, Object> snapshot() {
        return new LinkedHashMap<>();
    }

    /**
     * DB 列是 `NOT NULL DEFAULT '{}'::JSONB`，因此空值统一落成空 Map 而不是 null。
     */
    public static Map<String, Object> copySpecValues(Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(PurchaseAmountCalculator.SCALE);
    }
}
