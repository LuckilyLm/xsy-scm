package net.lab1024.sa.admin.module.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数据大屏-库存数据快照（只读聚合）。
 */
@Data
@Schema(description = "数据大屏-库存数据")
public class ScreenInventoryVO {

    @Schema(description = "库存总数量（所有仓库求和）")
    private BigDecimal totalQuantity;

    @Schema(description = "在库 SKU 数（inventory_balance 去重 sku）")
    private Long skuCount;

    @Schema(description = "启用仓库数")
    private Long warehouseCount;

    @Schema(description = "今日入库笔数（PURCHASE_IN）")
    private Long todayInboundCount;

    @Schema(description = "今日出库笔数（SALES_OUT）")
    private Long todayOutboundCount;

    @Schema(description = "按仓库库存分布")
    private List<WarehouseDistribution> warehouseDistribution;

    @Schema(description = "库存健康度（按库存预警阈值分档）")
    private InventoryHealth health;

    @Schema(description = "供应链网络-启用仓库节点")
    private List<WarehouseNode> warehouseNodes;

    @Data
    @Schema(description = "仓库分布")
    public static class WarehouseDistribution {
        @Schema(description = "仓库名称")
        private String warehouseName;
        @Schema(description = "库存数量")
        private BigDecimal quantity;
    }

    /**
     * 库存健康度。
     *
     * <p><b>分档规则完全复用库存预警阈值（V32），不另起一套算法</b>：基准是
     * {@code 可用量 = 现有量 − 预留量}，判定只有
     * {@link net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryWarningStatusEnum#evaluate}
     * 一处实现（正常 / 低于下限 / 高于上限）。
     *
     * <p>{@code outOfStockCount}（缺货 = 可用量 ≤ 0）是**单列指标**，与上面三档**可能重叠**：
     * 它是「低于下限」的极端情形，不是第四个互斥档位。之所以单列，是因为「一件都没有」
     * 和「低于下限但还有货」在补货决策上完全不同，而现有枚举无法区分。
     *
     * <p>{@code unconfiguredCount} 是存在余额行但**没有配置阈值**的 (仓库, SKU) 组合 ——
     * 它们既不正常也不预警，只是没有判定依据，因此必须单独暴露而不是塞进「正常」。
     */
    @Data
    @Schema(description = "库存健康度")
    public static class InventoryHealth {

        @Schema(description = "参与评估的 (仓库,SKU) 数 = 缺货+预警+积压+正常+未配置")
        private Long totalSkuCount;

        @Schema(description = "正常（在阈值区间内）")
        private Long normalCount;

        @Schema(description = "低于下限（补货预警，不含已缺货的）")
        private Long lowCount;

        @Schema(description = "高于上限（积压）")
        private Long highCount;

        @Schema(description = "缺货（可用量 ≤ 0，优先于其它分档）")
        private Long outOfStockCount;

        @Schema(description = "未配置阈值（无法判定，有余额但无阈值配置）")
        private Long unconfiguredCount;
    }

    /**
     * 供应链网络节点（大屏地图占位用）。
     *
     * <p>只返回**启用**仓库：停用仓不应出现在「当前供应链网络」里。
     * 目前仓库与客户都只有自由文本地址、没有经纬度或省市区结构化字段，
     * 因此这里只提供节点事实（名称 / 库存量 / 今日出库量），不提供坐标。
     */
    @Data
    @Schema(description = "供应链网络-仓库节点")
    public static class WarehouseNode {
        @Schema(description = "仓库名称")
        private String warehouseName;
        @Schema(description = "库存数量")
        private BigDecimal quantity;
        @Schema(description = "今日出库数量（SALES_OUT）")
        private BigDecimal todayOutboundQuantity;
    }
}
