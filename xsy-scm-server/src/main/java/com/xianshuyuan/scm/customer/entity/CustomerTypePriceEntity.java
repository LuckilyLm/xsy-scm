package com.xianshuyuan.scm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("customer_type_price")
public class CustomerTypePriceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerTypeId;
    private Long skuId;
    private BigDecimal unitPrice;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
    @Version private Integer version;
    @TableLogic private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getCustomerTypeId() { return customerTypeId; }
    public void setCustomerTypeId(Long value) { customerTypeId = value; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long value) { skuId = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime value) { effectiveFrom = value; }
    public OffsetDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(OffsetDateTime value) { effectiveTo = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { version = value; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean value) { deleted = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String value) { updatedBy = value; }
}
