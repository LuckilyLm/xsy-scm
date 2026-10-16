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

/** 分配记录连接采购明细和采购需求，数量计入该需求的跨采购单已分配总量。同一明细与需求组合不能重复出现。 */
@Data
@TableName(value = "xsy_purchase_demand_allocation", autoResultMap = true)
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
