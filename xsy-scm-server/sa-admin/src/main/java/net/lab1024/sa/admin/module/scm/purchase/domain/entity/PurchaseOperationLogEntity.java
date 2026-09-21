package net.lab1024.sa.admin.module.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseJsonbTypeHandler;

/**
 * 采购操作日志（W5 Target Design §5.10 / §7.12）。
 *
 * <p>**只追加**：没有 `version` / `deleted` / `updated_*`，也没有 update / delete 端点。
 *
 * <p>**归属（Q14）**：`purchaseOrderId` 与 `purchaseReceiptId` 的取值由 `operationType` 决定，
 * 由 `ck_purchase_operation_log_owner` 在 DB 层强制：
 * `DEMAND_GENERATE` 双 id 为空（此时采购单与收货单都还不存在）·
 * `DEMAND_ALLOCATE` 只有采购单 id（由 `purchaseOrderItemId` 反查）·
 * `CREATE/UPDATE/SUBMIT/CANCEL/SHORT_CLOSE/DELETE` 只有采购单 id ·
 * `RECEIPT_*` 双 id 非空。
 *
 * <p>`beforeData` / `afterData` 写**全量快照**（修 A-D15），格式与 W4 的 `order_operation_log` 一致。
 */
@Data
@TableName(value = "purchase_operation_log", autoResultMap = true)
public class PurchaseOperationLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseReceiptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operationType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operator;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    @TableField(typeHandler = PurchaseJsonbTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> beforeData;
    @TableField(typeHandler = PurchaseJsonbTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> afterData;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
}
