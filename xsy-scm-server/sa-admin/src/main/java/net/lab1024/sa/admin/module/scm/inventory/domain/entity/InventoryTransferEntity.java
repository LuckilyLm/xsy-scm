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
 * 调拨单（跨仓，两步式：发出 → 在途 → 收货）。
 *
 * <p><b>参考项目没有任何调拨实现</b>，本表是 V2 原创设计。两步式的两条理由见
 * {@code ScmInventoryTransferStatusEnum}：语义正确（货在卡车上时不在任何仓）、
 * 并发安全（每步只锁一个仓库的余额行，既有锁序纪律不用改）。
 *
 * <p><b>{@code shippedAt} / {@code shippedBy} 是转出流水的事实来源</b>，
 * {@code receivedAt} / {@code receivedBy} 是转入流水的事实来源 ——
 * 两笔流水的 {@code occurred_at} 与 {@code operator} 分别取自它们，
 * 禁止用 {@code now()} 或当前登录人顶替（否则「按业务时间查流水」会失真）。
 */
@Data
@TableName(value = "inventory_transfer", autoResultMap = true)
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
     * 发出时刻；仅 SHIPPED / RECEIVED 非空（DB CHECK 保证）。
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
