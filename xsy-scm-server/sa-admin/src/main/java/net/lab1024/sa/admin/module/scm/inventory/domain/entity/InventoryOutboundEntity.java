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
 * <p><b>两种产生方式</b>：仓库自己开草稿单再确认（{@code InventoryOutboundService}），
 * 以及配送发车一次性生成已确认单（{@code InventoryFulfillmentService}）。后者把
 * {@code sourceDocumentType = DELIVERY_ROUTE} 与 {@code sourceDocumentId = delivery_route.id}
 * 写在头上，由部分唯一索引 {@code uk_inventory_outbound_source_active} 保证
 * 「一条线路最多一张出库单」。
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

    /**
     * 出库单号（业务唯一，软删范围内唯一）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String outboundNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    /**
     * {@code ScmInventoryOutboundStatusEnum}。
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

    /**
     * 来源单据类型（{@code ScmInventorySourceDocumentTypeEnum}），手工出库单为 null。
     * 刻意不加 {@code ALWAYS}：来源是这张单的出身，一旦写入就不允许被后续整行更新抹掉。
     */
    private String sourceDocumentType;

    /**
     * 来源单据 id，如 {@code delivery_route.id}；与 {@link #sourceDocumentType} 成对（DB CHECK）。
     */
    private Long sourceDocumentId;

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
