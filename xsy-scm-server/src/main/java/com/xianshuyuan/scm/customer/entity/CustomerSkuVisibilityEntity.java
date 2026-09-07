package com.xianshuyuan.scm.customer.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.OffsetDateTime;

@TableName("customer_sku_visibility")
public class CustomerSkuVisibilityEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Long skuId;
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long v) {
        customerId = v;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long v) {
        skuId = v;
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
