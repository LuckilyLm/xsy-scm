package com.xsy.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.purchase.support.PurchaseJsonbTypeHandler;

/**
 * 采购明细按采购单与商品规格唯一，需求分配另按采购明细与需求唯一。容差范围内的超收合法，不能用静态数据库约束限制“已收量不超过计划量”。
 */
@Data
@TableName(value = "xsy_purchase_order_item", autoResultMap = true)
public class PurchaseOrderItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long spuId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spuCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String skuCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String skuNameSnapshot;
    @TableField(typeHandler = PurchaseJsonbTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> specValuesSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String purchaseUnitSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productTypeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchasePrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal lineAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer sortOrder;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime updatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String updatedBy;
}
