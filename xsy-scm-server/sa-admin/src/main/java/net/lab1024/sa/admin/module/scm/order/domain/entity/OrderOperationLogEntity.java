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
@TableName(value = "order_operation_log", autoResultMap = true)
public class OrderOperationLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operationType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operator;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    @TableField(typeHandler = OrderJsonbTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> beforeData;
    @TableField(typeHandler = OrderJsonbTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> afterData;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
}
