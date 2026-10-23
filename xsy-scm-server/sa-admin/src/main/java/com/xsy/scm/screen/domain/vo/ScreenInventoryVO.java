package com.xsy.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 大屏库存数据来自只读汇总，不直接修改库存。
 */
@Data
@Schema(description = "数据大屏-库存数据")
public class ScreenInventoryVO {

    @Schema(description = "库存总数量（所有仓库求和）")
    private BigDecimal totalQuantity;

    @Schema(description = "在库商品规格数（inventory_balance 去重 sku）")
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
     * 库存健康度按可用量与预警阈值计算。缺货单列用于提示零库存，可能与低库存统计重叠；无阈值配置的记录不能算作正常库存。
     */
    @Data
    @Schema(description = "库存健康度")
    public static class InventoryHealth {

        @Schema(description = "参与评估的 (仓库,商品规格) 数 = 缺货+预警+积压+正常+未配置")
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
     * <p>
     * 只返回启用仓库：停用仓不应出现在「当前供应链网络」里。仓库目前只有文本地址、没有经纬度，因此这里只提供节点数据（名称 / 库存量 / 今日出库量），不提供坐标。
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
