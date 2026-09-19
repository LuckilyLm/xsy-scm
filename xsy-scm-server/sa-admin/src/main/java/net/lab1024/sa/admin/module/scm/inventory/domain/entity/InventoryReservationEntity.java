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
 * 库存预留（来源单据行维度）。
 *
 * <p><b>为什么不写进 {@code inventory_movement}</b>：那张表是**物理数量账本** ——
 * {@code quantity > 0}、方向编码在类型里、且 {@code after = before ± quantity}。
 * 预留不改变物理数量，塞进去会直接破坏该快照约束的语义。因此预留单独建表，
 * 并在 {@code inventory_balance.reserved_quantity} 上维护活状态计数。
 *
 * <p><b>与流水相反，本表是「有状态记录」</b>：{@code ACTIVE → RELEASED / CONSUMED}。
 * 所以它有 {@code version} / {@code updated_*}，可以 update —— 与 movement 的只追加纪律
 * 是刻意不同的两套规则，不要互相套用。
 */
@Data
@TableName(value = "inventory_reservation", autoResultMap = true)
public class InventoryReservationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /** {@code ScmInventorySourceDocumentTypeEnum}；本波次为 {@code SALES_ORDER_ITEM}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceDocumentType;

    /** 来源单据 id（头级溯源，不参与防重）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceDocumentId;

    /** 来源单据行 id（防重锚点，唯一索引列）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceDocumentItemId;

    /** 恒为正（DB CHECK）；释放/消耗通过改 status 表达，不改数量。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /** 预留时的记账单位快照（= 该 (仓库, SKU) 的余额单位）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unitSnapshot;

    /** {@code ACTIVE / RELEASED / CONSUMED}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    /** 预留发生时刻（取来源单据的确认时刻，不是写入时刻）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime occurredAt;

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
