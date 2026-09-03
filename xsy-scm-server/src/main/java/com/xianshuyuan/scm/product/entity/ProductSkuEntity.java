package com.xianshuyuan.scm.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xianshuyuan.scm.common.persistence.JsonbStringMapTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@TableName(value = "product_sku", autoResultMap = true)
public class ProductSkuEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spuId;
    private String skuCode;
    private String barcode;
    @TableField(exist = false)
    private String productName;

    private String specName;
    @TableField(typeHandler = JsonbStringMapTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, String> specValues;
    private String saleUnit;
    private ProductType productType;
    private BigDecimal marketPrice;
    private ShelfStatus status;
    @TableField("is_default")
    private Boolean defaultSku;
    private Integer sortOrder;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getSpecName() { return specName; }
    public void setSpecName(String specName) { this.specName = specName; }
    public Map<String, String> getSpecValues() { return specValues; }
    public void setSpecValues(Map<String, String> specValues) { this.specValues = specValues; }
    public String getSaleUnit() { return saleUnit; }
    public void setSaleUnit(String saleUnit) { this.saleUnit = saleUnit; }
    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }
    public BigDecimal getMarketPrice() { return marketPrice; }
    public void setMarketPrice(BigDecimal marketPrice) { this.marketPrice = marketPrice; }
    public ShelfStatus getStatus() { return status; }
    public void setStatus(ShelfStatus status) { this.status = status; }
    public Boolean getDefaultSku() { return defaultSku; }
    public void setDefaultSku(Boolean defaultSku) { this.defaultSku = defaultSku; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
