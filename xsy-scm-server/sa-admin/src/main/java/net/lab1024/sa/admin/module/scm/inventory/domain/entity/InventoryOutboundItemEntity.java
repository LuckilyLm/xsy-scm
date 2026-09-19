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
 * 出库单明细行。
 *
 * <p>{@code unitSnapshot} 在**确认出库**时写入（= 该 (仓库, SKU) 的记账单位），
 * 草稿态允许为空 —— 因为草稿还没校验单位，提前写一个快照反而会误导。
 * 确认时若与余额记账单位不一致，直接失败（41001），不静默换算。
 */
@Data
@TableName(value = "inventory_outbound_item", autoResultMap = true)
public class InventoryOutboundItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long outboundId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /** 恒为正（DB CHECK），方向由 movement_type 表达。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /** 确认出库时写入的记账单位快照；草稿态为空。 */
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
