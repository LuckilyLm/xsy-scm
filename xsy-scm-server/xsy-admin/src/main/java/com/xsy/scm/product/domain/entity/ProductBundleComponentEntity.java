package com.xsy.scm.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

@Data
@TableName("xsy_product_bundle_component")
public class ProductBundleComponentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bundleSkuId;
    private Long componentSkuId;
    private BigDecimal quantityPerBundleSaleUnit;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
