package com.xianshuyuan.scm.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.xianshuyuan.scm.common.persistence.JsonbStringMapTypeHandler;
import com.xianshuyuan.scm.product.entity.ProductType;
import org.apache.ibatis.type.JdbcType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@TableName(value="purchase_order_item", autoResultMap=true)
public class PurchaseOrderItemEntity {
 @TableId(type=IdType.AUTO) private Long id; private Long purchaseOrderId,spuId,skuId;
 private String spuCodeSnapshot,productNameSnapshot,skuCodeSnapshot,skuNameSnapshot;
 @TableField(typeHandler=JsonbStringMapTypeHandler.class,jdbcType=JdbcType.OTHER) private Map<String,String> specValuesSnapshot;
 private String purchaseUnitSnapshot; private ProductType productTypeSnapshot; private BigDecimal plannedQuantity,receivedQuantity,purchasePrice,lineAmount; private Integer sortOrder,version; @TableLogic private Boolean deleted; private OffsetDateTime createdAt,updatedAt; private String createdBy,updatedBy;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getPurchaseOrderId(){return purchaseOrderId;} public void setPurchaseOrderId(Long v){purchaseOrderId=v;} public Long getSpuId(){return spuId;} public void setSpuId(Long v){spuId=v;} public Long getSkuId(){return skuId;} public void setSkuId(Long v){skuId=v;} public String getSpuCodeSnapshot(){return spuCodeSnapshot;} public void setSpuCodeSnapshot(String v){spuCodeSnapshot=v;} public String getProductNameSnapshot(){return productNameSnapshot;} public void setProductNameSnapshot(String v){productNameSnapshot=v;} public String getSkuCodeSnapshot(){return skuCodeSnapshot;} public void setSkuCodeSnapshot(String v){skuCodeSnapshot=v;} public String getSkuNameSnapshot(){return skuNameSnapshot;} public void setSkuNameSnapshot(String v){skuNameSnapshot=v;} public Map<String,String> getSpecValuesSnapshot(){return specValuesSnapshot;} public void setSpecValuesSnapshot(Map<String,String> v){specValuesSnapshot=v;} public String getPurchaseUnitSnapshot(){return purchaseUnitSnapshot;} public void setPurchaseUnitSnapshot(String v){purchaseUnitSnapshot=v;} public ProductType getProductTypeSnapshot(){return productTypeSnapshot;} public void setProductTypeSnapshot(ProductType v){productTypeSnapshot=v;} public BigDecimal getPlannedQuantity(){return plannedQuantity;} public void setPlannedQuantity(BigDecimal v){plannedQuantity=v;} public BigDecimal getReceivedQuantity(){return receivedQuantity;} public void setReceivedQuantity(BigDecimal v){receivedQuantity=v;} public BigDecimal getPurchasePrice(){return purchasePrice;} public void setPurchasePrice(BigDecimal v){purchasePrice=v;} public BigDecimal getLineAmount(){return lineAmount;} public void setLineAmount(BigDecimal v){lineAmount=v;} public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public Boolean getDeleted(){return deleted;} public void setDeleted(Boolean v){deleted=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;}
}
