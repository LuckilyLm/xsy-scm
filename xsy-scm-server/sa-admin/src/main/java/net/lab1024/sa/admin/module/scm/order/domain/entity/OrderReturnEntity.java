package net.lab1024.sa.admin.module.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data @TableName(value="order_return",autoResultMap=true)
public class OrderReturnEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String returnNo;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long orderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long customerId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String reason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String decisionReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal approvedAmount;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime approvedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime rejectedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime cancelledAt;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
