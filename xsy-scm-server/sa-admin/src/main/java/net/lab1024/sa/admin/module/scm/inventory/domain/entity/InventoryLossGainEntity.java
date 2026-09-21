package net.lab1024.sa.admin.module.scm.inventory.domain.entity;

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
 * 报损报溢单（库存管理深化 · 报损报溢波次）。
 *
 * <p><b>与出库单 / 盘点单的关键差别：这张单据有审批状态机</b>。
 * 出库与盘点是「录完即确认」，报损报溢必须经过审核才动库存 ——
 * 报损是**把货从账上抹掉**的动作，由一个人独立完成缺少制衡。
 *
 * <p><b>{@code auditor} / {@code audited_at} 是流水的事实来源</b>：审批通过时把这两个值
 * 传给 {@code InventoryCommandService}，作为 {@code inventory_movement.occurred_at}
 * 与 {@code operator} —— 与入库/出库/盘点同纪律，禁止用 {@code now()} 或当前登录人顶替。
 * 注意取的是**审核时刻与审核人**，不是创建时刻与创建人：库存是在审核那一刻变的。
 */
@Data
@TableName(value = "inventory_loss_gain", autoResultMap = true)
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
