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
 * 盘点单明细行：一行 = 一个 SKU 的「账面量 vs 实盘量」。
 *
 * <p><b>{@code bookQuantity} 是快照，不是实时值</b>：它在保存草稿那一刻从余额行读入，
 * 既作为清点时给仓管的参考，也作为差异计算的基线。确认时不再读它，而是持锁读**当时的**
 * 余额量（{@code live}），把差异施加到 live 上：
 * <pre>
 * delta = actualQuantity - bookQuantity
 * after = live + delta
 * </pre>
 * 这样「保存草稿 → 确认」之间发生的收货 / 出库不会被盘点抹掉。
 *
 * <p><b>{@code unitSnapshot} 在确认时写入</b>（= 该 (仓库, SKU) 的余额记账单位），
 * 草稿态允许为空 —— 与出库单明细同一取向。
 */
@Data
@TableName(value = "inventory_stocktake_item", autoResultMap = true)
public class InventoryStocktakeItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stocktakeId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /** 账面量快照（保存草稿那一刻的余额数量），差异基线；允许为 0。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal bookQuantity;

    /** 实盘量（清点结果）；允许为 0，不允许为负（DB CHECK）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal actualQuantity;

    /** 确认时写入的记账单位快照；草稿态为空。 */
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
