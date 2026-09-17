package net.lab1024.sa.admin.module.scm.purchase.constant;

/**
 * 采购操作日志类型（12 值）。
 *
 * <p>设计依据：W5 Target Design §7.12。取值与 V15 的
 * {@code ck_purchase_operation_log_type} 白名单逐字一致。
 *
 * <p>**归属规则（Q14）**：{@code purchase_operation_log.purchase_order_id} 的取值由本枚举决定，
 * 并由 {@code ck_purchase_operation_log_owner} 在 DB 层强制 ——
 * {@link #DEMAND_GENERATE} 双 id 为空（此时采购单与收货单都还不存在）·
 * {@link #DEMAND_ALLOCATE} 只有采购单 id（由 {@code purchaseOrderItemId} 反查）·
 * {@link #RECEIPT_CREATE} / {@link #RECEIPT_UPDATE} / {@link #RECEIPT_CONFIRM} /
 * {@link #RECEIPT_DELETE} 双 id 非空。
 */
public enum ScmPurchaseOperationTypeEnum {

    // ---- 采购单 ----
    CREATE, UPDATE, SUBMIT, CANCEL, SHORT_CLOSE, DELETE,

    // ---- 采购需求（无采购单 / 收货单可挂）----
    DEMAND_GENERATE, DEMAND_ALLOCATE,

    // ---- 采购收货（必属采购单）----
    RECEIPT_CREATE, RECEIPT_UPDATE, RECEIPT_CONFIRM, RECEIPT_DELETE
}
