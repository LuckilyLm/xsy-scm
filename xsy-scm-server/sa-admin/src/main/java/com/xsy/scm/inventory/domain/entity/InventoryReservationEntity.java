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
 * 库存预留不改变实物库存，因此单独记在预留表，不写入库存流水。预留记录可从占用变为释放或消耗；库存流水仍只能新增。
 */
@Data
@TableName(value = "xsy_inventory_reservation", autoResultMap = true)
public class InventoryReservationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /**
     * {@code ScmInventorySourceDocumentTypeEnum}；当前来源为 {@code SALES_ORDER_ITEM}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceDocumentType;

    /**
     * 来源单据 id（头级溯源，不参与防重）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceDocumentId;

    /**
     * 来源单据行 id（防重锚点，唯一索引列）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceDocumentItemId;

    /**
     * 恒为正（DB CHECK）；释放/消耗通过改 status 表达，不改数量。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /**
     * 预留时的记账单位快照（= 该 (仓库, SKU) 的余额单位）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unitSnapshot;

    /**
     * {@code ACTIVE / RELEASED / CONSUMED}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    /**
     * 预留发生时刻（取来源单据的确认时刻，不是写入时刻）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime occurredAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operator;

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
