package net.lab1024.sa.admin.module.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseJsonbTypeHandler;

/**
 * 采购收货单行（W5 Target Design §5.8）。
 *
 * <p>含 **5 个对账数量**，恒等式由 `ck_purchase_receipt_item_reconciliation` 在 DB 层强制（P24）：
 * <pre>
 * remaining_quantity      = GREATEST(planned_quantity − cumulative_received_quantity, 0)
 * over_receipt_quantity   = GREATEST(cumulative_received_quantity − planned_quantity, 0)
 * receipt_difference      = cumulative_received_quantity − planned_quantity      （可为负）
 * </pre>
 *
 * <p>**不存价格**：收货只记数量 / 重量，金额由采购行派生（A-D8）。
 *
 * <p>`actualWeight` / `weightUnit` / `weighingSource` 三字段**同生同灭**：
 * 标品全为 `null`（不是 `0.0000`），非标品三者齐全且 `weighingSource = MANUAL`。
 */
@Data @TableName(value="purchase_receipt_item",autoResultMap=true)
public class PurchaseReceiptItemEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaseReceiptId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaseOrderItemId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long skuId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String spuCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String productNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String skuCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String skuNameSnapshot;
    @TableField(typeHandler=PurchaseJsonbTypeHandler.class, updateStrategy=FieldStrategy.ALWAYS) private Map<String,Object> specValuesSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String purchaseUnitSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String productTypeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal plannedQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal receivedQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal cumulativeReceivedQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal remainingQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal overReceiptQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal receiptDifference;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal actualWeight;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String weightUnit;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String weighingSource;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String correctionReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer sortOrder;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
