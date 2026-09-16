package net.lab1024.sa.admin.module.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data @TableName(value="order_address_snapshot",autoResultMap=true)
public class OrderAddressSnapshotEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long orderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long customerId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiverName;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiverPhone;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String address;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
}
