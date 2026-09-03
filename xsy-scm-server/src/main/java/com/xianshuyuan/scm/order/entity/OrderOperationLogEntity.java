package com.xianshuyuan.scm.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.time.OffsetDateTime;

@TableName(value="order_operation_log", autoResultMap=true)
public class OrderOperationLogEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long orderId; private String operationType; private String operator; private String reason;
    @TableField(typeHandler=JsonbJsonNodeTypeHandler.class,jdbcType=JdbcType.OTHER) private JsonNode beforeData;
    @TableField(typeHandler=JsonbJsonNodeTypeHandler.class,jdbcType=JdbcType.OTHER) private JsonNode afterData;
    private OffsetDateTime createdAt; private String createdBy;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getOrderId(){return orderId;} public void setOrderId(Long v){orderId=v;} public String getOperationType(){return operationType;} public void setOperationType(String v){operationType=v;} public String getOperator(){return operator;} public void setOperator(String v){operator=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;} public JsonNode getBeforeData(){return beforeData;} public void setBeforeData(JsonNode v){beforeData=v;} public JsonNode getAfterData(){return afterData;} public void setAfterData(JsonNode v){afterData=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
}
