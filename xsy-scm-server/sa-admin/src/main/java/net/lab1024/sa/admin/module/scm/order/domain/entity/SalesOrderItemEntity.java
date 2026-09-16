package net.lab1024.sa.admin.module.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data @TableName(value="sales_order_item",autoResultMap=true)
public class SalesOrderItemEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long orderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long spuId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long skuId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String spuCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String productNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String skuCodeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String specNameSnapshot;
    @TableField(typeHandler=OrderJsonbTypeHandler.class, updateStrategy=FieldStrategy.ALWAYS) private Map<String,Object> specValuesSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String saleUnitSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String productTypeSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal orderedQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal actualQuantity;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String actualQuantitySource;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String actualQuantityReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal draftUnitPrice;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String draftPriceSource;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long draftPriceSourceId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Boolean manualPriceOverride;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String manualPriceReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal lockedUnitPrice;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String lockedPriceSource;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long lockedPriceSourceId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal orderedLineAmount;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal settlementLineAmount;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer sortOrder;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
