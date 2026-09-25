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

    /**
     * 恒为正（DB CHECK），方向由 movement_type 表达。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /**
     * 确认出库时写入的记账单位快照；草稿态为空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unitSnapshot;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /**
     * 来源销售订单 id，手工出库行为 null；与 {@link #salesOrderItemId} 成对（DB CHECK）。
     * 刻意不加 {@code ALWAYS} —— 来源写入后不允许被整行更新抹掉。
     */
    private Long salesOrderId;

    /**
     * 来源销售订单行 id：本行的 {@code quantity} 就是这一行的实发量。
     *
     * <p><b>同 SKU 的不同订单行不合并</b>，否则「哪张订单实发了多少」在库里失去答案，
     * 分拣的 REOPEN 守卫与 Finance R1 的成本归属都无从判定。
     */
    private Long salesOrderItemId;

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
