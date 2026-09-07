package com.xianshuyuan.scm.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName(value="purchase_receipt_confirmation", autoResultMap=true)
public class PurchaseReceiptConfirmationEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private String confirmationNo;
    private Long purchaseReceiptId;
    private String idempotencyScope, idempotencyKey, requestHash;
    private BigDecimal totalQuantity;
    private String status, operator;
    private OffsetDateTime confirmedAt, createdAt;
    @TableField(typeHandler=JsonbJsonNodeTypeHandler.class,jdbcType=JdbcType.OTHER) private JsonNode resultData;
    private String createdBy;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getConfirmationNo(){return confirmationNo;} public void setConfirmationNo(String v){confirmationNo=v;}
    public Long getPurchaseReceiptId(){return purchaseReceiptId;} public void setPurchaseReceiptId(Long v){purchaseReceiptId=v;}
    public String getIdempotencyScope(){return idempotencyScope;} public void setIdempotencyScope(String v){idempotencyScope=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public String getRequestHash(){return requestHash;} public void setRequestHash(String v){requestHash=v;}
    public BigDecimal getTotalQuantity(){return totalQuantity;} public void setTotalQuantity(BigDecimal v){totalQuantity=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getOperator(){return operator;} public void setOperator(String v){operator=v;}
    public OffsetDateTime getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(OffsetDateTime v){confirmedAt=v;}
    public JsonNode getResultData(){return resultData;} public void setResultData(JsonNode v){resultData=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
}
