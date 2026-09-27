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
 * 关系以 SKU 为粒度；快照列冻结供应商与 SKU 的展示信息，使主数据改名后关联行仍然可读。
 */
@Data
@TableName(value = "supplier_sku", autoResultMap = true)
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

    private String supplierCodeSnapshot;

    private String supplierNameSnapshot;

    private String skuCodeSnapshot;

    private String skuNameSnapshot;

    @TableField(typeHandler = JsonbStringMapTypeHandler.class)
    private Map<
            String,
            String> specValuesSnapshot;

    private String purchaseUnit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal referencePrice;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaserId;

    /**
     * 是否默认采购来源。
     *
     * <p>
     * 同一供应商允许多个默认来源，因此这里不做互斥校验，也不建立限制基数的唯一索引。
     */
    @TableField("is_default")
    private Boolean defaultFlag;

    private String status;
}
