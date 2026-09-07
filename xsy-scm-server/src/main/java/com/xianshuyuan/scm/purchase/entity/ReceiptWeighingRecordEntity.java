package com.xianshuyuan.scm.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("receipt_weighing_record")
public class ReceiptWeighingRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long purchaseReceiptItemId;
    private BigDecimal rawReading;
    private BigDecimal confirmedReading;
    private String unit;
    private BigDecimal scalePrecision;
    private ReceiptWeighingSource source;
    private String deviceSessionId;
    private String modificationReason;
    private OffsetDateTime recordedAt;
    private String operator;
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

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPurchaseReceiptItemId() {
        return purchaseReceiptItemId;
    }

    public void setPurchaseReceiptItemId(Long purchaseReceiptItemId) {
        this.purchaseReceiptItemId = purchaseReceiptItemId;
    }

    public BigDecimal getRawReading() {
        return rawReading;
    }

    public void setRawReading(BigDecimal rawReading) {
        this.rawReading = rawReading;
    }

    public BigDecimal getConfirmedReading() {
        return confirmedReading;
    }

    public void setConfirmedReading(BigDecimal confirmedReading) {
        this.confirmedReading = confirmedReading;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getScalePrecision() {
        return scalePrecision;
    }

    public void setScalePrecision(BigDecimal scalePrecision) {
        this.scalePrecision = scalePrecision;
    }

    public ReceiptWeighingSource getSource() {
        return source;
    }

    public void setSource(ReceiptWeighingSource source) {
        this.source = source;
    }

    public String getDeviceSessionId() {
        return deviceSessionId;
    }

    public void setDeviceSessionId(String deviceSessionId) {
        this.deviceSessionId = deviceSessionId;
    }

    public String getModificationReason() {
        return modificationReason;
    }

    public void setModificationReason(String modificationReason) {
        this.modificationReason = modificationReason;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(OffsetDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
