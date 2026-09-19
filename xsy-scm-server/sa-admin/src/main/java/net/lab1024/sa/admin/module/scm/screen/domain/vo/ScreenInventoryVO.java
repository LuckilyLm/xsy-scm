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

    @Data
    @Schema(description = "仓库分布")
    public static class WarehouseDistribution {
        @Schema(description = "仓库名称")
        private String warehouseName;
        @Schema(description = "库存数量")
        private BigDecimal quantity;
    }
}
