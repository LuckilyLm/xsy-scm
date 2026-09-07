package com.xianshuyuan.scm.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.OffsetDateTime;

@TableName("purchase_receipt")
public class PurchaseReceiptEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String receiptNo;
    private Long purchaseOrderId;
    private String purchaseOrderNoSnapshot;
    private Long warehouseId;
    private String warehouseCodeSnapshot;
    private String warehouseNameSnapshot;
    private PurchaseReceiptStatus status;
    private OffsetDateTime receivedAt;
    private OffsetDateTime confirmedAt;
    private String operator;
    private String remark;
    @Version private Integer version;
    @TableLogic private Boolean deleted;
    private OffsetDateTime createdAt, updatedAt;
    private String createdBy, updatedBy;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getReceiptNo(){return receiptNo;} public void setReceiptNo(String v){receiptNo=v;}
    public Long getPurchaseOrderId(){return purchaseOrderId;} public void setPurchaseOrderId(Long v){purchaseOrderId=v;}
    public String getPurchaseOrderNoSnapshot(){return purchaseOrderNoSnapshot;} public void setPurchaseOrderNoSnapshot(String v){purchaseOrderNoSnapshot=v;}
    public Long getWarehouseId(){return warehouseId;} public void setWarehouseId(Long v){warehouseId=v;}
    public String getWarehouseCodeSnapshot(){return warehouseCodeSnapshot;} public void setWarehouseCodeSnapshot(String v){warehouseCodeSnapshot=v;}
    public String getWarehouseNameSnapshot(){return warehouseNameSnapshot;} public void setWarehouseNameSnapshot(String v){warehouseNameSnapshot=v;}
    public PurchaseReceiptStatus getStatus(){return status;} public void setStatus(PurchaseReceiptStatus v){status=v;}
    public OffsetDateTime getReceivedAt(){return receivedAt;} public void setReceivedAt(OffsetDateTime v){receivedAt=v;}
    public OffsetDateTime getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(OffsetDateTime v){confirmedAt=v;}
    public String getOperator(){return operator;} public void setOperator(String v){operator=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public Boolean getDeleted(){return deleted;} public void setDeleted(Boolean v){deleted=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
    public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;}
}
