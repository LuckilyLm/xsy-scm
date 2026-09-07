package com.xianshuyuan.scm.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xianshuyuan.scm.common.persistence.JsonbStringMapTypeHandler;
import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@TableName(value = "supplier_sku", autoResultMap = true)
public class SupplierSkuEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private Long skuId;
    private String supplierCodeSnapshot;
    private String supplierNameSnapshot;
    private String skuCodeSnapshot;
    private String skuNameSnapshot;
    @TableField(typeHandler = JsonbStringMapTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, String> specValuesSnapshot;
    private String purchaseUnit;
    private BigDecimal referencePrice;
    private Long purchaserId;
    @TableField("is_default")
    private Boolean defaultSupplier;
    private EnabledStatus status;
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

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getSupplierCodeSnapshot() {
        return supplierCodeSnapshot;
    }

    public void setSupplierCodeSnapshot(String supplierCodeSnapshot) {
        this.supplierCodeSnapshot = supplierCodeSnapshot;
    }

    public String getSupplierNameSnapshot() {
        return supplierNameSnapshot;
    }

    public void setSupplierNameSnapshot(String supplierNameSnapshot) {
        this.supplierNameSnapshot = supplierNameSnapshot;
    }

    public String getSkuCodeSnapshot() {
        return skuCodeSnapshot;
    }

    public void setSkuCodeSnapshot(String skuCodeSnapshot) {
        this.skuCodeSnapshot = skuCodeSnapshot;
    }

    public String getSkuNameSnapshot() {
        return skuNameSnapshot;
    }

    public void setSkuNameSnapshot(String skuNameSnapshot) {
        this.skuNameSnapshot = skuNameSnapshot;
    }

    public Map<String, String> getSpecValuesSnapshot() {
        return specValuesSnapshot == null ? null : Map.copyOf(specValuesSnapshot);
    }

    public void setSpecValuesSnapshot(Map<String, String> specValuesSnapshot) {
        this.specValuesSnapshot = specValuesSnapshot == null
                ? null
                : Map.copyOf(specValuesSnapshot);
    }

    public String getPurchaseUnit() {
        return purchaseUnit;
    }

    public void setPurchaseUnit(String purchaseUnit) {
        this.purchaseUnit = purchaseUnit;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(BigDecimal referencePrice) {
        this.referencePrice = referencePrice;
    }

    public Long getPurchaserId() {
        return purchaserId;
    }

    public void setPurchaserId(Long purchaserId) {
        this.purchaserId = purchaserId;
    }

    public Boolean getDefaultSupplier() {
        return defaultSupplier;
    }

    public void setDefaultSupplier(Boolean defaultSupplier) {
        this.defaultSupplier = defaultSupplier;
    }

    public EnabledStatus getStatus() {
        return status;
    }

    public void setStatus(EnabledStatus status) {
        this.status = status;
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
