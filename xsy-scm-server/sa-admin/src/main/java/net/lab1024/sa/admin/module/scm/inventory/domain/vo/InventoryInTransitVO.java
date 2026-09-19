package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 在途库存报表行（按调拨单明细聚合，不进 inventory_balance）。
 *
 * <p>用于把「在途调拨量」以报表形式暴露给对账与库存查询页，
 * 避免引入虚拟在途仓（decisions.md 未决事项裁决方案②）。
 */
@Data
@Schema(description = "在途库存行")
public class InventoryInTransitVO {

    @Schema(description = "调拨单号")
    private String transferNo;

    @Schema(description = "源仓库 ID")
    private Long fromWarehouseId;

    @Schema(description = "源仓库名称")
    private String fromWarehouseName;

    @Schema(description = "目标仓库 ID")
    private Long toWarehouseId;

    @Schema(description = "目标仓库名称")
    private String toWarehouseName;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "在途数量")
    private BigDecimal quantity;

    @Schema(description = "单位")
    private String unit;
}
