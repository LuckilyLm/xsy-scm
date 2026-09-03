package com.xianshuyuan.scm.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.xianshuyuan.scm.common.persistence.JsonbStringMapTypeHandler;
import com.xianshuyuan.scm.product.entity.ProductType;
import org.apache.ibatis.type.JdbcType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@TableName(value="sales_order_item", autoResultMap=true)
public class SalesOrderItemEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long orderId; private Long spuId; private Long skuId;
    private String spuCodeSnapshot; private String productNameSnapshot; private String skuCodeSnapshot; private String specNameSnapshot;
    @TableField(typeHandler=JsonbStringMapTypeHandler.class,jdbcType=JdbcType.OTHER) private Map<String,String> specValuesSnapshot;
    private String saleUnitSnapshot; private ProductType productTypeSnapshot;
    private BigDecimal orderedQuantity; private BigDecimal actualQuantity; private QuantitySource actualQuantitySource; private String actualQuantityReason;
    private BigDecimal draftUnitPrice; private PriceSource draftPriceSource; private Long draftPriceSourceId;
    private Boolean manualPriceOverride; private String manualPriceReason;
    private BigDecimal lockedUnitPrice; private PriceSource lockedPriceSource; private Long lockedPriceSourceId;
    private BigDecimal orderedLineAmount; private BigDecimal settlementLineAmount; private Integer sortOrder;
    @Version private Integer version; @TableLogic private Boolean deleted;
    private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private String createdBy; private String updatedBy;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getOrderId(){return orderId;} public void setOrderId(Long v){orderId=v;}
    public Long getSpuId(){return spuId;} public void setSpuId(Long v){spuId=v;} public Long getSkuId(){return skuId;} public void setSkuId(Long v){skuId=v;}
    public String getSpuCodeSnapshot(){return spuCodeSnapshot;} public void setSpuCodeSnapshot(String v){spuCodeSnapshot=v;} public String getProductNameSnapshot(){return productNameSnapshot;} public void setProductNameSnapshot(String v){productNameSnapshot=v;}
    public String getSkuCodeSnapshot(){return skuCodeSnapshot;} public void setSkuCodeSnapshot(String v){skuCodeSnapshot=v;} public String getSpecNameSnapshot(){return specNameSnapshot;} public void setSpecNameSnapshot(String v){specNameSnapshot=v;}
    public Map<String,String> getSpecValuesSnapshot(){return specValuesSnapshot;} public void setSpecValuesSnapshot(Map<String,String> v){specValuesSnapshot=v;} public String getSaleUnitSnapshot(){return saleUnitSnapshot;} public void setSaleUnitSnapshot(String v){saleUnitSnapshot=v;}
    public ProductType getProductTypeSnapshot(){return productTypeSnapshot;} public void setProductTypeSnapshot(ProductType v){productTypeSnapshot=v;} public BigDecimal getOrderedQuantity(){return orderedQuantity;} public void setOrderedQuantity(BigDecimal v){orderedQuantity=v;}
    public BigDecimal getActualQuantity(){return actualQuantity;} public void setActualQuantity(BigDecimal v){actualQuantity=v;} public QuantitySource getActualQuantitySource(){return actualQuantitySource;} public void setActualQuantitySource(QuantitySource v){actualQuantitySource=v;} public String getActualQuantityReason(){return actualQuantityReason;} public void setActualQuantityReason(String v){actualQuantityReason=v;}
    public BigDecimal getDraftUnitPrice(){return draftUnitPrice;} public void setDraftUnitPrice(BigDecimal v){draftUnitPrice=v;} public PriceSource getDraftPriceSource(){return draftPriceSource;} public void setDraftPriceSource(PriceSource v){draftPriceSource=v;} public Long getDraftPriceSourceId(){return draftPriceSourceId;} public void setDraftPriceSourceId(Long v){draftPriceSourceId=v;}
    public Boolean getManualPriceOverride(){return manualPriceOverride;} public void setManualPriceOverride(Boolean v){manualPriceOverride=v;} public String getManualPriceReason(){return manualPriceReason;} public void setManualPriceReason(String v){manualPriceReason=v;}
    public BigDecimal getLockedUnitPrice(){return lockedUnitPrice;} public void setLockedUnitPrice(BigDecimal v){lockedUnitPrice=v;} public PriceSource getLockedPriceSource(){return lockedPriceSource;} public void setLockedPriceSource(PriceSource v){lockedPriceSource=v;} public Long getLockedPriceSourceId(){return lockedPriceSourceId;} public void setLockedPriceSourceId(Long v){lockedPriceSourceId=v;}
    public BigDecimal getOrderedLineAmount(){return orderedLineAmount;} public void setOrderedLineAmount(BigDecimal v){orderedLineAmount=v;} public BigDecimal getSettlementLineAmount(){return settlementLineAmount;} public void setSettlementLineAmount(BigDecimal v){settlementLineAmount=v;} public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public Boolean getDeleted(){return deleted;} public void setDeleted(Boolean v){deleted=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;}
}
