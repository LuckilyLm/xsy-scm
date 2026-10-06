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
 * 采购需求 ↔ 采购单行的数量分配。
 *
 * <p>
 * 分配是集合：{@code purchase_order_item} 与 {@code purchase_demand_allocation} 是 <b>1:N</b> —— 一行采购行（一个
 * SKU）可以承接<b>多个</b>需求，每个 {@code (purchase_order_item_id, purchase_demand_id)} 组合至多一条活动 allocation（由
 * {@code uk_purchase_demand_allocation_source_active} 强制）。 「一行多需求」由<b>多行 allocation</b> 表达。
 */
@Data
@TableName(value = "purchase_demand_allocation", autoResultMap = true)
public class PurchaseDemandAllocationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseDemandId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseOrderItemId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderItemId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal allocatedQuantity;
    @TableField(typeHandler = PurchaseJsonbTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> demandSnapshot;
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
