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
 * 采购需求 ↔ 采购单行的数量分配（W5 Target Design §5.4）。
 *
 * <p>**Q13**：`purchase_order_item` 与 `purchase_demand_allocation` 是 **1:N** ——
 * 一行采购行（一个 SKU）可以承接**多个**需求，每个 `(purchase_order_item_id, purchase_demand_id)`
 * 组合至多一条活动 allocation（由 `uk_purchase_demand_allocation_source_active` 强制）。
 * 「一行多需求」由**多行 allocation** 表达。
 */
@Data @TableName(value="purchase_demand_allocation",autoResultMap=true)
public class PurchaseDemandAllocationEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaseDemandId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaseOrderItemId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long salesOrderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long salesOrderItemId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long skuId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal allocatedQuantity;
    @TableField(typeHandler=PurchaseJsonbTypeHandler.class, updateStrategy=FieldStrategy.ALWAYS) private Map<String,Object> demandSnapshot;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
