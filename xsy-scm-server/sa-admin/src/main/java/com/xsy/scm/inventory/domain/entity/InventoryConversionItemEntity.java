package com.xsy.scm.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 一条转换明细包含来源商品、目标商品、两边数量和单位；数量均为正，方向由转出、转入动作决定。单位按单据声明校验，目标没有余额时可由转入创建余额。
 */
@Data
@TableName(value = "xsy_inventory_conversion_item", autoResultMap = true)
public class InventoryConversionItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long conversionId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceSkuId;

    /**
     * 源数量，恒为正。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal sourceQuantity;

    /**
     * 源单位（单据显式声明）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceUnit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long targetSkuId;

    /**
     * 目标数量，恒为正；与源数量构成折算关系。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal targetQuantity;

    /**
     * 目标单位（单据显式声明）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String targetUnit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

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
