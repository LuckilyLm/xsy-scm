package com.xsy.scm.inventory.domain.entity;

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
 * 库存余额（粒度 = warehouse + sku）。
 *
 * <p><b>余额是活状态，不是单据</b>：因此**没有**任何商品/仓库快照列，
 * 展示用的编码与名称由查询服务实时联 {@code product_sku} / {@code product_spu} / {@code warehouse} 取
 * （与 {@code warehouse} 列表同一取向）。单据侧的快照纪律不受影响。
 *
 * <p>余额按仓库和 SKU 记录当前数量、预留量及移动加权成本；商品展示信息通过查询实时关联主数据。
 *
 * <p>每个仓库与 SKU 组合只有一个记账单位，其他业务维度由各自单据或配置记录：
 * <ul>
 *   <li>商品和仓库名称不做快照，读取时关联主数据；</li>
 *   <li>预警阈值由独立配置记录维护；</li>
 *   <li>批次与保质期信息不属于余额记录。</li>
 * </ul>
 *
 * <p>{@code unit} 是单位不变量载体：一个 {@code (warehouse_id, sku_id)} 锁定一个记账单位，
 * 后续异单位入库必须显式失败（41001），不能静默相加。
 */
@Data
@TableName(value = "inventory_balance", autoResultMap = true)
public class InventoryBalanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /**
     * 记账单位：首笔入库写入，之后不可变（异单位入库直接失败）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;

    /**
     * 当前账面数量，数据库约束保证其不小于零。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /**
     * 已预留量；可用量为 {@code quantity - reserved_quantity}。
     *
     * <p>预留不改变物理库存，因此这里是一个独立计数，而不是从流水推导 ——
     * 它由 {@code InventoryReservationService} 在**持有本行锁之后**增减，
     * 并由 DB 层两条 CHECK 兜底：{@code reserved_quantity >= 0} 与
     * {@code reserved_quantity <= quantity}（后者保证可用量不为负，即「已预留的货不能被出库吃掉」）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal reservedQuantity;

    /**
     * 移动加权平均成本，按记账单位计。
     *
     * <p>平均成本随余额一同维护，因为移动加权依赖入库顺序，出库成本必须在出库时确定。
     *
     * <p>由库存写入路径在持有余额行锁之后维护（与数量同一时机）。
     * 入库按 {@code (旧量·旧均价 + 入量·入价) / 新量} 重算；出库**不变**。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgCost;

    /**
     * 乐观锁列 —— **纵深防御，不是第一道并发机制**。
     *
     * <p>余额更新只发生在**持有行锁之后**（{@code SELECT ... FOR UPDATE} 再 {@code WHERE id = ?} 自增），
     * 因此正常路径下不可能撞版本。它的价值在于：若未来出现一条「未持锁的更新路径」，
     * 该路径会因为版本条件失败而**报错**，而不是悄悄脏写。
     */
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
