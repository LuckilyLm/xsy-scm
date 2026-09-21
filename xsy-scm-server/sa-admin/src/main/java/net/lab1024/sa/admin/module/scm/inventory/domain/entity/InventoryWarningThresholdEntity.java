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
 * 库存预警阈值配置（按仓库 + SKU）。
 *
 * <p><b>为什么不在 {@code inventory_balance} 上</b>（参考项目是那么做的）：余额行是**派生状态**，
 * 它只能由流水产生 —— {@code InventoryBalanceDao} 里没有任何「建行 / 赋值」方法，
 * 唯一的建行入口是入库与调拨转入。若阈值放在余额表上，配置路径就必须为了写阈值而创建余额行，
 * 那会造出「没有任何流水支撑的余额行」，直接破坏「余额是流水的净和」这条不变量。
 *
 * <p><b>按 (仓库, SKU) 而不是只按 SKU</b>：参考项目的「单仓库」是 G-03 的范围限制而非业务规则；
 * V2 已有跨仓调拨，两仓的合理下限本来就可能不同（前置仓 vs 中心仓）。
 *
 * <p><b>预警状态不在这里</b>：{@code NORMAL / LOW / HIGH} 是 {@code (阈值, 可用量)} 的派生值，
 * 读时计算（见 {@code ScmInventoryWarningStatusEnum}），不建列也不维护。
 */
@Data
@TableName(value = "inventory_warning_threshold", autoResultMap = true)
public class InventoryWarningThresholdEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /**
     * 预警下限（可用量低于它触发 LOW）；{@code null} 表示不设下限。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal warnMin;

    /**
     * 预警上限（可用量高于它触发 HIGH）；{@code null} 表示不设上限。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal warnMax;

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
