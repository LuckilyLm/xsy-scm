package net.lab1024.sa.admin.module.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data
@TableName(value = "sales_order", autoResultMap = true)
public class SalesOrderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long originalOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplementReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedTotalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementTotalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String settleModeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime expectDeliveryTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sellerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime cancelledAt;
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
