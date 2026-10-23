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
 * 报损报溢单创建后待审核，批准时才生成库存流水；驳回不影响库存。已批准或驳回的单据不能重改，历史库存流水只追加不回写。
 */
@Data
@TableName(value = "xsy_inventory_loss_gain", autoResultMap = true)
public class InventoryLossGainEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 单据号（业务唯一，软删范围内唯一）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lossGainNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    /**
     * {@code ScmInventoryLossGainTypeEnum}：LOSS 报损 / OVERFLOW 报溢。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String adjustType;

    /**
     * {@code ScmInventoryLossGainStatusEnum}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    /**
     * 报损报溢原因，必填（DB CHECK 也挡空串）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /**
     * 审核时刻；仅已审核（通过 / 驳回）非空（DB CHECK 保证）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime auditedAt;

    /**
     * 审核人；仅已审核非空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String auditor;

    /**
     * 审核意见（驳回理由）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String auditOpinion;

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
