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
 * 出库单可由仓库创建草稿并确认，也可在配送发车时直接生成已确认出库单。
 */
@Data
@TableName(value = "xsy_inventory_outbound", autoResultMap = true)
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
     * 确认时刻；仅 CONFIRMED 非空（数据库 CHECK 保证）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime confirmedAt;

    /**
     * 确认人；仅 CONFIRMED 非空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operator;

    /**
     * 来源单据类型（{@code ScmInventorySourceDocumentTypeEnum}），手工出库单为 null。刻意不加 {@code ALWAYS}： 来源是这张单的源头，一旦写入就不允许被后续整行更新抹掉。
     */
    private String sourceDocumentType;

    /**
     * 来源单据 id，如 {@code xsy_delivery_route.id}；与 {@link #sourceDocumentType} 成对（数据库 CHECK）。
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
