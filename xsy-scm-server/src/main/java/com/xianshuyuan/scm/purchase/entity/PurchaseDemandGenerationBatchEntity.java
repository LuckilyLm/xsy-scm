package com.xianshuyuan.scm.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName(value = "purchase_demand_generation_batch", autoResultMap = true)
public class PurchaseDemandGenerationBatchEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String idempotencyScope, idempotencyKey, requestHash;
    private OffsetDateTime startAt, endAt;
    private Long warehouseId;
    private Boolean calculateInventory;
    private Integer sourceLineCount, createdCount, skippedCount;
    private BigDecimal originalQuantity, deductedQuantity;
    @TableField(typeHandler = JsonbJsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode resultData;
    private OffsetDateTime createdAt;
    private String createdBy;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getIdempotencyScope(){return idempotencyScope;} public void setIdempotencyScope(String v){idempotencyScope=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public String getRequestHash(){return requestHash;} public void setRequestHash(String v){requestHash=v;}
    public OffsetDateTime getStartAt(){return startAt;} public void setStartAt(OffsetDateTime v){startAt=v;}
    public OffsetDateTime getEndAt(){return endAt;} public void setEndAt(OffsetDateTime v){endAt=v;}
    public Long getWarehouseId(){return warehouseId;} public void setWarehouseId(Long v){warehouseId=v;}
    public Boolean getCalculateInventory(){return calculateInventory;} public void setCalculateInventory(Boolean v){calculateInventory=v;}
    public Integer getSourceLineCount(){return sourceLineCount;} public void setSourceLineCount(Integer v){sourceLineCount=v;}
    public Integer getCreatedCount(){return createdCount;} public void setCreatedCount(Integer v){createdCount=v;}
    public Integer getSkippedCount(){return skippedCount;} public void setSkippedCount(Integer v){skippedCount=v;}
    public BigDecimal getOriginalQuantity(){return originalQuantity;} public void setOriginalQuantity(BigDecimal v){originalQuantity=v;}
    public BigDecimal getDeductedQuantity(){return deductedQuantity;} public void setDeductedQuantity(BigDecimal v){deductedQuantity=v;}
    public JsonNode getResultData(){return resultData;} public void setResultData(JsonNode v){resultData=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
}
