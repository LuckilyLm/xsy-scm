package com.xianshuyuan.scm.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.time.OffsetDateTime;

@TableName(value="idempotency_record", autoResultMap=true)
public class IdempotencyRecordEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private String operationScope; private String idempotencyKey; private String requestHash; private String resultType; private Long resultId;
    @TableField(typeHandler=JsonbJsonNodeTypeHandler.class,jdbcType=JdbcType.OTHER) private JsonNode resultData;
    @Version private Integer version; @TableLogic private Boolean deleted;
    private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private String createdBy; private String updatedBy;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getOperationScope(){return operationScope;} public void setOperationScope(String v){operationScope=v;} public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;} public String getRequestHash(){return requestHash;} public void setRequestHash(String v){requestHash=v;} public String getResultType(){return resultType;} public void setResultType(String v){resultType=v;} public Long getResultId(){return resultId;} public void setResultId(Long v){resultId=v;} public JsonNode getResultData(){return resultData;} public void setResultData(JsonNode v){resultData=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public Boolean getDeleted(){return deleted;} public void setDeleted(Boolean v){deleted=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;}
}
