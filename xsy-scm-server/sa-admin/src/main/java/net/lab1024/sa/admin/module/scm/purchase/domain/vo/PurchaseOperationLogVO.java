package net.lab1024.sa.admin.module.scm.purchase.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 采购操作日志（W5 Target Design §7.2 / §7.12）。
 *
 * <p>`purchaseOrderId` / `purchaseReceiptId` 的取值由 `operationType` 决定（Q14）：
 * `DEMAND_GENERATE` 两者皆空 · `DEMAND_ALLOCATE` 只有采购单 id · `RECEIPT_*` 双 id 非空。
 * `beforeData` / `afterData` 是全量快照。
 */
@Data
public class PurchaseOperationLogVO {
    private Long id;
    private Long purchaseOrderId;
    private Long purchaseReceiptId;
    private String operationType;
    private String operator;
    private String reason;
    private Map<String, Object> beforeData;
    private Map<String, Object> afterData;
    private OffsetDateTime createdAt;
}
