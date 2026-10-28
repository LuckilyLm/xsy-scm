package com.xsy.scm.purchase.manager;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.support.PlannedQuantity;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.purchase.constant.ScmPurchaseDemandStatusEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptStatusEnum;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOperationLogEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseReceiptEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成采购、需求和收货单据的历史信息。 采购单提交后不再从商品、供应商等主档刷新内容；收货明细不保存价格。
 */
public final class PurchaseSnapshotFactory {

    /**
     * 与 SQL 里的 {@code AT TIME ZONE 'Asia/Shanghai'} 字面量保持一致。
     */
    public static final ZoneId ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private PurchaseSnapshotFactory() {
    }

    /**
     * 创建或编辑草稿时更新供应商、仓库信息，提交后冻结。 采购员由服务端确定，不能信任客户端传入的 ID。
     */
    public static PurchaseOrderEntity order(String supplierCode, String supplierName, String warehouseCode,
        String warehouseName, Long purchaserId, PurchaseOrderAddForm form) {
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
        order.setStatus(ScmPurchaseStatusEnum.DRAFT.name());
        order.setVersion(0);
        order.setDeleted(false);
        return order;
    }

    /**
     * 采购明细的采购单位来自供应商商品关系，需求单位来自销售订单，两者独立保存并校验一致，不做隐式换算。
     */
    public static PurchaseOrderItemEntity item(PurchaseOrderAddForm.Item form, ProductSkuOptionVO sku, String spuCode,
        SupplierSkuEntity supplierSku) {
        PurchaseOrderItemEntity item = new PurchaseOrderItemEntity();
        item.setId(form.getId());
        item.setVersion(form.getVersion());
        item.setSkuId(sku.getSkuId());
        item.setSpuId(sku.getSpuId());
        item.setSpuCodeSnapshot(spuCode);
        item.setProductNameSnapshot(sku.getProductName());
        item.setSkuCodeSnapshot(sku.getSkuCode());
        // 供应商关联行的 SKU 展示名称使用 SPU 名称。
        item.setSkuNameSnapshot(sku.getProductName());
        item.setSpecValuesSnapshot(copySpecValues(sku.getSpecValues()));
        item.setPurchaseUnitSnapshot(supplierSku.getPurchaseUnit());
        item.setProductTypeSnapshot(sku.getProductType());
        item.setPlannedQuantity(PurchaseOrderValidator.decimal(form.getQuantity(), true));
        item.setReceivedQuantity(zero());
        // 待定价行没有价格，金额记 0；缺价不等于免费，页面必须按 pricePending 标注
        boolean pricePending = Boolean.TRUE.equals(form.getPricePending());
        item.setPricePending(pricePending);
        item.setPurchasePrice(pricePending ? null : PurchaseOrderValidator.decimal(form.getPrice(), false));
        item.setLineAmount(pricePending
            ? zero()
            : PurchaseAmountCalculator.lineAmount(item.getPlannedQuantity(), item.getPurchasePrice()));
        item.setDeleted(false);
        return item;
    }

    /**
     * 收货单头：供应商 / 仓库 / 采购单号从采购单继承。
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
        receipt.setStatus(ScmReceiptStatusEnum.DRAFT.name());
        receipt.setRemark(PurchaseOrderValidator.trim(remark));
        receipt.setVersion(0);
        receipt.setDeleted(false);
        return receipt;
    }

    /**
     * 收货单明细从采购单继承商品、单位和计划量，累计收货从零开始；剩余量与超收量按对账公式计算。
     */
    public static PurchaseReceiptItemEntity receiptItem(PurchaseReceiptEntity receipt,
        PurchaseOrderItemEntity orderItem, int sortOrder) {
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
     * {@code demand_date} = {@code source_confirmed_at} 在 Asia/Shanghai 下的日期。
     *
     * <p>
     * 不能使用汇总窗口的第一天（{@code date(startAt)}），否则跨多日窗口会把全部需求压平成同一天。 数据库的 {@code ck_purchase_demand_date} 会复核，两侧必须一致。
     */
    public static LocalDate demandDate(OffsetDateTime sourceConfirmedAt) {
        return sourceConfirmedAt.atZoneSameInstant(ASIA_SHANGHAI).toLocalDate();
    }

    /**
     * 采购需求的数量取「计划数量」：已称重用实发量，还没称重（非标行在分拣台才称）用订购量。
     *
     * <p>
     * 采购是照着订单备货，不是照着称重结果报损；要求必须先有实重才能生成需求，等于把 14:00 的采购卡在 20:00 的称重之后。
     */
    public static BigDecimal plannedQuantity(SalesOrderItemEntity source) {
        return PlannedQuantity.of(source.getActualQuantity(), source.getOrderedQuantity());
    }

    /**
     * 采购需求在生成时保存销售订单的单位和计划数量，不能用采购单位覆盖。采购单位由供应商商品关系决定，正式分配时校验两者一致。
     */
    public static PurchaseDemandEntity demand(SalesOrderItemEntity source, SalesOrderEntity order, Long supplierId,
        Long warehouseId, Long purchaserId) {
        PurchaseDemandEntity demand = new PurchaseDemandEntity();
        demand.setSalesOrderId(order.getId());
        demand.setSalesOrderItemId(source.getId());
        demand.setSpuId(source.getSpuId());
        demand.setSkuId(source.getSkuId());
        demand.setSalesOrderNoSnapshot(order.getOrderNo());
        demand.setSpuCodeSnapshot(source.getSpuCodeSnapshot());
        demand.setProductNameSnapshot(source.getProductNameSnapshot());
        demand.setSkuCodeSnapshot(source.getSkuCodeSnapshot());
        // SKU 展示名称取 SPU 名称。
        demand.setSkuNameSnapshot(source.getProductNameSnapshot());
        demand.setSpecValuesSnapshot(copySpecValues(source.getSpecValuesSnapshot()));
        demand.setDemandUnitSnapshot(source.getSaleUnitSnapshot());
        demand.setProductTypeSnapshot(source.getProductTypeSnapshot());
        demand.setRequiredQuantity(plannedQuantity(source));
        demand.setAllocatedQuantity(zero());
        demand.setSupplierId(supplierId);
        demand.setWarehouseId(warehouseId);
        demand.setPurchaserId(purchaserId);
        demand.setStatus(ScmPurchaseDemandStatusEnum.PENDING.name());
        demand.setSourceConfirmedAt(order.getConfirmedAt());
        demand.setDemandDate(demandDate(order.getConfirmedAt()));
        demand.setVersion(0);
        demand.setDeleted(false);
        return demand;
    }

    /**
     * 分配行上的 {@code demand_snapshot}。
     *
     * <p>
     * 让每条 allocation 自带证据：即使需求行之后被改或被删，也能从分配行回答「当初挂的是哪张订单行、什么单位、多少量」。 数量按 4 位定点字符串存（不是 JSON 数字），避免浮点语义破坏定点约定。
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
     * 4 位定点字符串；{@code null} 保持 {@code null}（不写成 {@code "0.0000"}）。
     */
    public static String fixed(BigDecimal amount) {
        return amount == null
            ? null
            : amount.setScale(PurchaseAmountCalculator.SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 操作日志行。
     *
     * <p>
     * 日志的归属由调用方决定，本方法不做推断：{@code DEMAND_GENERATE} 传两个 {@code null}；{@code DEMAND_ALLOCATE} 传反查得到的
     * {@code purchaseOrderId} + {@code null}；{@code RECEIPT_*} 传两个非空。数据库的 {@code ck_purchase_operation_log_owner} 会复核，
     * 传错就写不进去，这是故意的（让错误在写入点暴露，而不是留一条脏日志）。
     *
     * @param before
     *            变更前的数据；{@code null} 表示「无前态」（如 {@code CREATE} / {@code DEMAND_GENERATE}）
     * @param after
     *            变更后的数据
     */
    public static PurchaseOperationLogEntity operationLog(ScmPurchaseOperationTypeEnum operationType,
        Long purchaseOrderId, Long purchaseReceiptId, String reason, Map<String, Object> before,
        Map<String, Object> after) {
        PurchaseOperationLogEntity log = new PurchaseOperationLogEntity();
        log.setOperationType(operationType.name());
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
     * 可变的 JSONB 记录容器（{@code Map.of} 不可变，日志记录需要逐项 put）。
     */
    public static Map<String, Object> snapshot() {
        return new LinkedHashMap<>();
    }

    /**
     * 数据库列是 {@code NOT NULL DEFAULT '{}'::JSONB}，因此空值统一写成空 Map 而不是 null。
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
