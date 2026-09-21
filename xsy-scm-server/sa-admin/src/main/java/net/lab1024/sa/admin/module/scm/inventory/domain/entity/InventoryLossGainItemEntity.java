package net.lab1024.sa.admin.module.scm.inventory.domain.entity;

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
 * 报损报溢单明细行。
 *
 * <p><b>{@code quantity} 恒为正</b>：方向由单据头的 {@code adjustType} 决定，
 * 不在行上用正负号表达 —— 那样会出现「同一张单里两行方向相反」的状态，
 * 既难校验（审批人看不出这张单到底是加还是减）也难展示。
 *
 * <p><b>{@code unitSnapshot} 在审批通过时写入</b>（= 该 (仓库, SKU) 的余额记账单位），
 * 待审核态允许为空 —— 与出库单 / 盘点单明细同一取向。
 */
@Data
@TableName(value = "inventory_loss_gain_item", autoResultMap = true)
public class InventoryLossGainItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long lossGainId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /**
     * 申报数量，恒为正（DB CHECK）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /**
     * 审批通过时写入的记账单位快照；待审核态为空。
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
