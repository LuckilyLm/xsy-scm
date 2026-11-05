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
 * 预警阈值按仓库和商品规格设置，不放在库存余额表中。修改阈值不会改变库存数量。
 */
@Data
@TableName(value = "xsy_inventory_warning_threshold", autoResultMap = true)
public class InventoryWarningThresholdEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /**
     * 预警下限（可用量低于它触发 LOW）；{@code null} 表示不设下限。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal warnMin;

    /**
     * 预警上限（可用量高于它触发 HIGH）；{@code null} 表示不设上限。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal warnMax;

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
