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
 * 盘点明细保存当时账面数量和实盘数量，确认前仍需校验库存版本。盘点差异由两者计算，确认后才形成不可改的库存流水。
 */
@Data
@TableName(value = "xsy_inventory_stocktake_item", autoResultMap = true)
public class InventoryStocktakeItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stocktakeId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /**
     * 账面量快照（保存草稿那一刻的余额数量），差异基线；允许为 0。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal bookQuantity;

    /**
     * 实盘量（清点结果）；允许为 0，不允许为负（DB CHECK）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal actualQuantity;

    /**
     * 确认时写入的记账单位快照；草稿态为空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unitSnapshot;

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
