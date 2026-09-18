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
 * 库存余额（W6 Target Design §2.1，粒度 = warehouse + sku）。
 *
 * <p><b>余额是活状态，不是单据</b>：因此**没有**任何商品/仓库快照列，
 * 展示用的编码与名称由查询服务实时联 {@code product_sku} / {@code product_spu} / {@code warehouse} 取
 * （与 W5 {@code warehouse} 列表同一取向）。单据侧的快照纪律不受影响。
 *
 * <p><b>没有的列（都是裁决结果，不是遗漏）</b>：
 * <ul>
 *   <li>{@code weight} —— Q2：数量 = 采购单位口径（非标品实重即数量），双记账属分拣波次；</li>
 *   <li>{@code avg_cost} / {@code total_cost} —— Q3：成本事实由 movement 的 {@code unit_cost} 承载；</li>
 *   <li>{@code warn_min} / {@code warn_max} —— Q4：预警能力整体延后；</li>
 *   <li>{@code batch_id} —— G-03：批次/保质期永久不启用，不留死列。</li>
 * </ul>
 *
 * <p><b>{@code unit} 是 Q13 的单位不变量载体</b>：一个 {@code (warehouse_id, sku_id)} 锁定一个记账单位，
 * 后续异单位入库必须显式失败（41001），绝不静默相加。
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

    /** Q13 记账单位：首笔入库写入，之后不可变（异单位入库直接失败）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;

    /** Q10：{@code quantity >= 0} 本期冻结（DB CHECK）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

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
