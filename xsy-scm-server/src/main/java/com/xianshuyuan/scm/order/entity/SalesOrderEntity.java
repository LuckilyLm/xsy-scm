package com.xianshuyuan.scm.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("sales_order")
public class SalesOrderEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String orderNo;
    private Long customerId;
    private String customerCodeSnapshot;
    private String customerNameSnapshot;
    private OrderSource orderSource;
    private Long originalOrderId;
    private String supplementReason;
    private OrderStatus status;
    private BigDecimal orderedTotalAmount;
    private BigDecimal settlementTotalAmount;
    private String cancelReason;
    private OffsetDateTime submittedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    @Version private Integer version;
    @TableLogic private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getOrderNo(){return orderNo;} public void setOrderNo(String v){orderNo=v;}
    public Long getCustomerId(){return customerId;} public void setCustomerId(Long v){customerId=v;}
    public String getCustomerCodeSnapshot(){return customerCodeSnapshot;} public void setCustomerCodeSnapshot(String v){customerCodeSnapshot=v;}
    public String getCustomerNameSnapshot(){return customerNameSnapshot;} public void setCustomerNameSnapshot(String v){customerNameSnapshot=v;}
    public OrderSource getOrderSource(){return orderSource;} public void setOrderSource(OrderSource v){orderSource=v;}
    public Long getOriginalOrderId(){return originalOrderId;} public void setOriginalOrderId(Long v){originalOrderId=v;}
    public String getSupplementReason(){return supplementReason;} public void setSupplementReason(String v){supplementReason=v;}
    public OrderStatus getStatus(){return status;} public void setStatus(OrderStatus v){status=v;}
    public BigDecimal getOrderedTotalAmount(){return orderedTotalAmount;} public void setOrderedTotalAmount(BigDecimal v){orderedTotalAmount=v;}
    public BigDecimal getSettlementTotalAmount(){return settlementTotalAmount;} public void setSettlementTotalAmount(BigDecimal v){settlementTotalAmount=v;}
    public String getCancelReason(){return cancelReason;} public void setCancelReason(String v){cancelReason=v;}
    public OffsetDateTime getSubmittedAt(){return submittedAt;} public void setSubmittedAt(OffsetDateTime v){submittedAt=v;}
    public OffsetDateTime getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(OffsetDateTime v){confirmedAt=v;}
    public OffsetDateTime getCancelledAt(){return cancelledAt;} public void setCancelledAt(OffsetDateTime v){cancelledAt=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public Boolean getDeleted(){return deleted;} public void setDeleted(Boolean v){deleted=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
    public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;}
}
