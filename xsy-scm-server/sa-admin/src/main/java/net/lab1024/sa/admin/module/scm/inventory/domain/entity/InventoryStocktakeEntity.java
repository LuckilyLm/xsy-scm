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
 * 盘点单（库存管理深化 · 盘点波次）。
 *
 * <p><b>差异不落库到明细的「差异列」上</b>：差异是 {@code actual_quantity - book_quantity}
 * 的派生值，不另设列（冗余列会与两个真值列失去同步）。确认时真正写下来的是
 * 流水的 {@code before_quantity} / {@code after_quantity} —— 那才是权威账。
 *
 * <p><b>{@code operator} / {@code confirmed_at} 是流水的事实来源</b>：确认盘点时把这两个值
 * 传给 {@code InventoryCommandService}，作为 {@code inventory_movement.occurred_at}
 * 与 {@code operator} —— 与入库/出库同纪律，禁止用 {@code now()} 或当前登录人顶替。
 */
@Data
@TableName(value = "inventory_stocktake", autoResultMap = true)
public class InventoryStocktakeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 盘点单号（业务唯一，软删范围内唯一）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stocktakeNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    /**
     * {@code ScmInventoryStocktakeStatusEnum}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /**
     * 确认时刻；仅 CONFIRMED 非空（DB CHECK 保证）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime confirmedAt;

    /**
     * 确认人；仅 CONFIRMED 非空。
     */
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
