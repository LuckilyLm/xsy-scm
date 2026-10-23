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
 * 收货明细保留计划量、本次实收量、累计收货量、剩余量、超收量及差异。数据库约束保证这些值一致；不在收货行另存采购价格。
 */
@Data
@TableName(value = "xsy_purchase_receipt_item", autoResultMap = true)
public class PurchaseReceiptItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseReceiptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseOrderItemId;
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
    private BigDecimal cumulativeReceivedQuantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal remainingQuantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal overReceiptQuantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receiptDifference;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String weightUnit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String weighingSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String correctionReason;
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
