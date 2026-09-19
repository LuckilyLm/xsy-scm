package net.lab1024.sa.admin.module.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 数据大屏-采购数据快照（只读聚合）。
 */
@Data
@Schema(description = "数据大屏-采购数据")
public class ScreenPurchaseVO {

    @Schema(description = "今日采购单数")
    private Long todayPurchaseOrderCount;

    @Schema(description = "今日采购金额")
    private BigDecimal todayPurchaseAmount;

    @Schema(description = "累计采购单数")
    private Long totalPurchaseOrderCount;

    @Schema(description = "累计采购金额")
    private BigDecimal totalPurchaseAmount;

    @Schema(description = "今日收货单数")
    private Long todayReceiptCount;
}
