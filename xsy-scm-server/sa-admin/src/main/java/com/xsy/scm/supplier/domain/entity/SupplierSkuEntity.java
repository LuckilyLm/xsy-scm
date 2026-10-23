package com.xsy.scm.supplier.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import com.xsy.scm.common.json.JsonbStringMapTypeHandler;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 商品-供应商关系（SKU 级）。
 *
 * <p>
 * 关系行同时保存供应商与 SKU 的名称等信息，主数据改名后仍然可读。
 */
@Data
@TableName(value = "xsy_supplier_sku", autoResultMap = true)
public class SupplierSkuEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    private Long supplierId;

    private Long skuId;

    /** 供应商给该商品的外部货号，不是系统 SKU 编码，也不是批次号。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierItemCode;

    private String supplierCodeSnapshot;

    private String supplierNameSnapshot;

    private String skuCodeSnapshot;

    private String skuNameSnapshot;

    @TableField(typeHandler = JsonbStringMapTypeHandler.class)
    private Map<String, String> specValuesSnapshot;

    private String purchaseUnit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal referencePrice;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaserId;

    /**
     * 是否默认采购来源。同一供应商允许多个默认来源，因此不做互斥校验。
     */
    @TableField("is_default")
    private Boolean defaultFlag;

    private String status;
}
