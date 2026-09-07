package com.xianshuyuan.scm.order.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("order_return_item")
public class OrderReturnItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long orderItemId;
    private BigDecimal requestedQuantity;
    private BigDecimal approvedQuantity;
    private BigDecimal lockedUnitPrice;
    private BigDecimal approvedAmount;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public Long getReturnId() {
        return returnId;
    }

    public void setReturnId(Long v) {
        returnId = v;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long v) {
        orderItemId = v;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(BigDecimal v) {
        requestedQuantity = v;
    }

    public BigDecimal getApprovedQuantity() {
        return approvedQuantity;
    }

    public void setApprovedQuantity(BigDecimal v) {
        approvedQuantity = v;
    }

    public BigDecimal getLockedUnitPrice() {
        return lockedUnitPrice;
    }

    public void setLockedUnitPrice(BigDecimal v) {
        lockedUnitPrice = v;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal v) {
        approvedAmount = v;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer v) {
        version = v;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean v) {
        deleted = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        createdAt = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime v) {
        updatedAt = v;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String v) {
        createdBy = v;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String v) {
        updatedBy = v;
    }
}
