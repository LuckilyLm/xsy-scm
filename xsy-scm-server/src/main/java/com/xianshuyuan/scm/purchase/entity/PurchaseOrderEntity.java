package com.xianshuyuan.scm.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.*;

@TableName("purchase_order")
public class PurchaseOrderEntity {
    @TableId(type = IdType.AUTO)
    Long id;
    String orderNo;
    Long supplierId, purchaserId, warehouseId;
    String supplierCodeSnapshot, supplierNameSnapshot, warehouseCodeSnapshot, warehouseNameSnapshot, remark, cancelReason;
    LocalDate plannedArrivalDate;
    PurchaseOrderStatus status;
    BigDecimal totalAmount;
    OffsetDateTime submittedAt, cancelledAt, shortClosedAt, createdAt, updatedAt;
    String shortCloseReason;
    @Version
    Integer version;
    @TableLogic
    Boolean deleted;
    String createdBy, updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String v) {
        orderNo = v;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long v) {
        supplierId = v;
    }

    public Long getPurchaserId() {
        return purchaserId;
    }

    public void setPurchaserId(Long v) {
        purchaserId = v;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long v) {
        warehouseId = v;
    }

    public String getSupplierCodeSnapshot() {
        return supplierCodeSnapshot;
    }

    public void setSupplierCodeSnapshot(String v) {
        supplierCodeSnapshot = v;
    }

    public String getSupplierNameSnapshot() {
        return supplierNameSnapshot;
    }

    public void setSupplierNameSnapshot(String v) {
        supplierNameSnapshot = v;
    }

    public String getWarehouseCodeSnapshot() {
        return warehouseCodeSnapshot;
    }

    public void setWarehouseCodeSnapshot(String v) {
        warehouseCodeSnapshot = v;
    }

    public void setWarehouseNameSnapshot(String v) {
        warehouseNameSnapshot = v;
    }

    public String getWarehouseNameSnapshot() {
        return warehouseNameSnapshot;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String v) {
        remark = v;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String v) {
        cancelReason = v;
    }

    public LocalDate getPlannedArrivalDate() {
        return plannedArrivalDate;
    }

    public void setPlannedArrivalDate(LocalDate v) {
        plannedArrivalDate = v;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus v) {
        status = v;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal v) {
        totalAmount = v;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime v) {
        submittedAt = v;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime v) {
        cancelledAt = v;
    }

    public OffsetDateTime getShortClosedAt() { return shortClosedAt; }
    public void setShortClosedAt(OffsetDateTime v) { shortClosedAt = v; }
    public String getShortCloseReason() { return shortCloseReason; }
    public void setShortCloseReason(String v) { shortCloseReason = v; }

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
