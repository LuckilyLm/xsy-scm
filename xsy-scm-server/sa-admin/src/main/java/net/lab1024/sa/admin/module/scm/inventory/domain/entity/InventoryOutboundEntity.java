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
 * 出库单（独立出库单，W6-1 之后的出库波次）。
 *
 * <p><b>为什么是独立单据而不是挂在发货上</b>：发货模块尚未开发，把出库挂上去会让库存能力
 * 被一个不存在的模块阻塞。这里先做「仓库能自己开单出库」的最小闭环，等发货模块落地后，
 * 由发货确认去创建并确认出库单即可，无需改本表结构。
 *
 * <p><b>{@code operator} / {@code confirmed_at} 是流水的事实来源</b>：
 * 确认出库时把这两个值传给 {@code InventoryCommandService}，作为
 * {@code inventory_movement.occurred_at} 与 {@code operator} —— 与入库同纪律，
 * 禁止用 {@code now()} 或当前登录人顶替。
 */
@Data
@TableName(value = "inventory_outbound", autoResultMap = true)
public class InventoryOutboundEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出库单号（业务唯一，软删范围内唯一）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String outboundNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    /** {@code ScmInventoryOutboundStatusEnum}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 确认时刻；仅 CONFIRMED 非空（DB CHECK 保证）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime confirmedAt;

    /** 确认人；仅 CONFIRMED 非空。 */
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
