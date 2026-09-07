package com.xianshuyuan.scm.customer.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.OffsetDateTime;

@TableName("customer")
public class CustomerEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerCode;
    private String name;
    private Long customerTypeId;
    private EnabledStatus status;
    private VisibilityPolicy visibilityPolicy;
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

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String v) {
        customerCode = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public Long getCustomerTypeId() {
        return customerTypeId;
    }

    public void setCustomerTypeId(Long v) {
        customerTypeId = v;
    }

    public EnabledStatus getStatus() {
        return status;
    }

    public void setStatus(EnabledStatus v) {
        status = v;
    }

    public VisibilityPolicy getVisibilityPolicy() {
        return visibilityPolicy;
    }

    public void setVisibilityPolicy(VisibilityPolicy v) {
        visibilityPolicy = v;
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
