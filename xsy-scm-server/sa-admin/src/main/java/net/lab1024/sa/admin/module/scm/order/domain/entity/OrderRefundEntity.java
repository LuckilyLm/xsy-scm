package net.lab1024.sa.admin.module.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data @TableName(value="order_refund",autoResultMap=true)
public class OrderRefundEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String refundNo;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long returnId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long orderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long customerId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal refundAmount;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String externalReference;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime completedAt;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
