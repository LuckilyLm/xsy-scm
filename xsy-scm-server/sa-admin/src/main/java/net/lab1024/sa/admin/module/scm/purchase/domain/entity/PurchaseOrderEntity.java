package net.lab1024.sa.admin.module.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购单头（W5 Target Design §5.5）。
 *
 * <p>6 状态 `DRAFT/SUBMITTED/PARTIALLY_RECEIVED/RECEIVED/SHORT_CLOSED/CANCELLED`（Q2），
 * 由 {@code ck_purchase_order_status} 与 `PurchaseOrderStateMachine` 双重强制。
 *
 * <p>**没有 `confirmed_at`**：采购单的收货完成时间分散在各收货单的 `confirmed_at` 上（F4）。
 * 供应商 / 仓库快照在创建 / 编辑时刷新，`submit` 后永不回读主数据（P4）。
 */
@Data @TableName(value="purchase_order",autoResultMap=true)
public class PurchaseOrderEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String orderNo;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long supplierId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String supplierCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String supplierNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaserId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long warehouseId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String warehouseCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String warehouseNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private LocalDate plannedArrivalDate;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal totalAmount;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String remark;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String cancelReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String shortCloseReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime submittedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime cancelledAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime shortClosedAt;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
