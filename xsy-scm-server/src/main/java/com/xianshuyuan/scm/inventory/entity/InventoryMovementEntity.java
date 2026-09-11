package com.xianshuyuan.scm.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("inventory_movement")
public class InventoryMovementEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String movementNo;
    private OffsetDateTime occurredAt;
    private Long warehouseId, skuId;
    private String warehouseCodeSnapshot, warehouseNameSnapshot, skuCodeSnapshot, skuNameSnapshot;
    private InventoryMovementType movementType;
    private String sourceDocumentType;
    private Long sourceDocumentId, sourceDocumentItemId, confirmationId, receiptId;
    private BigDecimal quantityBefore, quantityChange, quantityAfter, unitCost;
    private String unit, operator, remark;
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt, updatedAt;
    private String createdBy, updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public String getMovementNo() {
        return movementNo;
    }

    public void setMovementNo(String v) {
        movementNo = v;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime v) {
        occurredAt = v;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long v) {
        warehouseId = v;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long v) {
        skuId = v;
    }

    public String getWarehouseCodeSnapshot() {
        return warehouseCodeSnapshot;
    }

    public void setWarehouseCodeSnapshot(String v) {
        warehouseCodeSnapshot = v;
    }

    public String getWarehouseNameSnapshot() {
        return warehouseNameSnapshot;
    }

    public void setWarehouseNameSnapshot(String v) {
        warehouseNameSnapshot = v;
    }

    public String getSkuCodeSnapshot() {
        return skuCodeSnapshot;
    }

    public void setSkuCodeSnapshot(String v) {
        skuCodeSnapshot = v;
    }

    public String getSkuNameSnapshot() {
        return skuNameSnapshot;
    }

    public void setSkuNameSnapshot(String v) {
        skuNameSnapshot = v;
    }

    public InventoryMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(InventoryMovementType v) {
        movementType = v;
    }

    public String getSourceDocumentType() {
        return sourceDocumentType;
    }

    public void setSourceDocumentType(String v) {
        sourceDocumentType = v;
    }

    public Long getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Long v) {
        sourceDocumentId = v;
    }

    public Long getSourceDocumentItemId() {
        return sourceDocumentItemId;
    }

    public void setSourceDocumentItemId(Long v) {
        sourceDocumentItemId = v;
    }

    public Long getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(Long v) {
        confirmationId = v;
    }

    public Long getReceiptId() { return receiptId; }

    public void setReceiptId(Long v) { receiptId = v; }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public void setQuantityBefore(BigDecimal v) {
        quantityBefore = v;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(BigDecimal v) {
        quantityChange = v;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public void setQuantityAfter(BigDecimal v) {
        quantityAfter = v;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal v) {
        unitCost = v;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String v) {
        unit = v;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String v) {
        operator = v;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String v) {
        remark = v;
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
