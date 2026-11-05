package com.xsy.scm.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 调拨分发出和收货两步，源仓发出后在途，目标仓收货后完成。单据保存两端仓库、实际发出和收货记录；不能把在途调拨当作已入库。
 */
@Data
@TableName(value = "xsy_inventory_transfer", autoResultMap = true)
public class InventoryTransferEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 调拨单号（业务唯一，软删范围内唯一）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String transferNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long fromWarehouseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long toWarehouseId;

    /**
     * {@code ScmInventoryTransferStatusEnum}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /**
     * 发出时刻；仅 SHIPPED / RECEIVED 非空（数据库 CHECK 保证）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime shippedAt;

    /**
     * 发出人；仅 SHIPPED / RECEIVED 非空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shippedBy;

    /**
     * 收货时刻；仅 RECEIVED 非空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime receivedAt;

    /**
     * 收货人；仅 RECEIVED 非空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String receivedBy;

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
