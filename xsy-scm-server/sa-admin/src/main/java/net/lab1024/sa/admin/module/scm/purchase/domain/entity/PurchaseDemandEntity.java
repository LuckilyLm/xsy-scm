package net.lab1024.sa.admin.module.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseJsonbTypeHandler;

/**
 * 采购需求（W5 Target Design §5.3）。
 *
 * <p>来源：{@code sales_order_item}（W4，只读）。`sales_order_item_id` 上有部分唯一索引，
 * 一条订单行最多生成一条活动需求。
 *
 * <p>**Q6a**：`demandDate` = `sourceConfirmedAt` 在 {@code Asia/Shanghai} 下的日期，
 * 由 DB CHECK `ck_purchase_demand_date` 强制，**不是**汇总窗口的第一天。
 *
 * <p>**Q17**：`demandUnitSnapshot` 是**需求单位**（来源 `sales_order_item.sale_unit_snapshot`），
 * 永不被采购单位覆盖。
 */
@Data @TableName(value="purchase_demand",autoResultMap=true)
public class PurchaseDemandEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long salesOrderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long salesOrderItemId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long spuId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long skuId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String salesOrderNoSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String spuCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String productNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String skuCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String skuNameSnapshot;
    @TableField(typeHandler=PurchaseJsonbTypeHandler.class, updateStrategy=FieldStrategy.ALWAYS) private Map<String,Object> specValuesSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String demandUnitSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String productTypeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal requiredQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal allocatedQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long supplierId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long warehouseId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaserId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private LocalDate demandDate;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime sourceConfirmedAt;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
