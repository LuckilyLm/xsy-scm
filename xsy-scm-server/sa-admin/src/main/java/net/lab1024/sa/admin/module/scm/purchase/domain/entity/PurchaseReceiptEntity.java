package net.lab1024.sa.admin.module.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.time.OffsetDateTime;

/**
 * 采购收货单头（W5 Target Design §5.7）。
 *
 * <p>2 状态 `DRAFT/CONFIRMED`（Q7 / Q7a）。**没有 `receipt_mode` / `putaway_status`**：
 * W5 只做 DIRECT、不实现库存，建一个恒为固定值的字段等于死字段
 * （同 W4 拒绝 `fulfillment_status` 的纪律）；W6 用 `ALTER TABLE` 追加。
 *
 * <p>供应商 / 仓库快照在收货单创建时从采购单继承。
 */
@Data @TableName(value="purchase_receipt",autoResultMap=true)
public class PurchaseReceiptEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiptNo;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaseOrderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String purchaseOrderNoSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long supplierId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String supplierCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String supplierNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long warehouseId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String warehouseCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String warehouseNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime receivedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime confirmedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String operator;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String remark;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
