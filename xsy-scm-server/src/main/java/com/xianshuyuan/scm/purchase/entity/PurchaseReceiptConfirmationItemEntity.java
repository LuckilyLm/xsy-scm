package com.xianshuyuan.scm.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("purchase_receipt_confirmation_item")
public class PurchaseReceiptConfirmationItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long confirmationId, purchaseReceiptItemId, purchaseOrderItemId, weighingRecordId;
    private BigDecimal plannedQuantity, effectiveQuantity, actualWeight;
    private String unit;
    private ReceiptWeighingSource weighingSource;
    private String correctionReason;
    private OffsetDateTime createdAt;
    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public Long getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(Long v) {
        confirmationId = v;
    }

    public Long getPurchaseReceiptItemId() {
        return purchaseReceiptItemId;
    }

    public void setPurchaseReceiptItemId(Long v) {
        purchaseReceiptItemId = v;
    }

    public Long getPurchaseOrderItemId() {
        return purchaseOrderItemId;
    }

    public void setPurchaseOrderItemId(Long v) {
        purchaseOrderItemId = v;
    }

    public Long getWeighingRecordId() {
        return weighingRecordId;
    }

    public void setWeighingRecordId(Long v) {
        weighingRecordId = v;
    }

    public BigDecimal getPlannedQuantity() {
        return plannedQuantity;
    }

    public void setPlannedQuantity(BigDecimal v) {
        plannedQuantity = v;
    }

    public BigDecimal getEffectiveQuantity() {
        return effectiveQuantity;
    }

    public void setEffectiveQuantity(BigDecimal v) {
        effectiveQuantity = v;
    }

    public BigDecimal getActualWeight() {
        return actualWeight;
    }

    public void setActualWeight(BigDecimal v) {
        actualWeight = v;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String v) {
        unit = v;
    }

    public ReceiptWeighingSource getWeighingSource() {
        return weighingSource;
    }

    public void setWeighingSource(ReceiptWeighingSource v) {
        weighingSource = v;
    }

    public String getCorrectionReason() {
        return correctionReason;
    }

    public void setCorrectionReason(String v) {
        correctionReason = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        createdAt = v;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String v) {
        createdBy = v;
    }
}
