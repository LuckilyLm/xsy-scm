package net.lab1024.sa.admin.module.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.time.OffsetDateTime;

/**
 * 采购收货单头（W5 Target Design §5.7；B1 扩展 HD-B1-01/02/03）。
 *
 * <p>商业状态 `status`（`DRAFT/CONFIRMED`）与入库生命周期 `receiptMode` /
 * `putawayStatus` **解耦**：`CONFIRMED` 只表示收货已确认，不等于库存已入账。
 * <ul>
 *   <li>{@code receiptMode=DIRECT}：confirm 同事务完成入库（putaway=COMPLETED）；</li>
 *   <li>{@code receiptMode=WAREHOUSE_CONFIRM}：confirm 后 putaway=PENDING，仓库二次确认才入库。</li>
 * </ul>
 *
 * <p>供应商 / 仓库快照在收货单创建时从采购单继承。
 */
@Data
@TableName(value = "purchase_receipt", autoResultMap = true)
public class PurchaseReceiptEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String receiptNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String purchaseOrderNoSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String receiptMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String putawayStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime putawayAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String putawayBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime receivedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operator;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime updatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String updatedBy;
}
