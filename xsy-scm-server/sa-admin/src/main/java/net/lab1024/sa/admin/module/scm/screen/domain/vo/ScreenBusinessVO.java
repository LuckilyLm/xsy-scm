package net.lab1024.sa.admin.module.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数据大屏-经营数据快照（只读聚合）。
 *
 * <p>口径：今日订单与销售额取 sales_order.created_at 在当日区间内的已确认/已完成口径
 * （CONFIRMED），金额为 settlement_total_amount（结算口径）与 ordered_total_amount（下单口径）双列。
 * 排行取区间内按销售额倒序的前 N 条客户/商品。
 */
@Data
@Schema(description = "数据大屏-经营数据")
public class ScreenBusinessVO {

    @Schema(description = "今日订单数（CONFIRMED）")
    private Long todayOrderCount;

    @Schema(description = "今日下单金额")
    private BigDecimal todayOrderedAmount;

    @Schema(description = "今日结算金额")
    private BigDecimal todaySettlementAmount;

    @Schema(description = "累计订单数（CONFIRMED）")
    private Long totalOrderCount;

    @Schema(description = "累计结算金额")
    private BigDecimal totalSettlementAmount;

    @Schema(description = "客户总数")
    private Long customerCount;

    @Schema(description = "供应商总数")
    private Long supplierCount;

    @Schema(description = "商品 SKU 总数")
    private Long skuCount;

    @Schema(description = "今日成交客户数（今日有 CONFIRMED 订单的客户去重数）")
    private Long todayCustomerCount;

    @Schema(description = "今日活跃供应商数（今日有采购单的供应商去重数）")
    private Long todaySupplierCount;

    @Schema(description = "客户销售额排行（今日，前10）")
    private List<RankItem> topCustomers;

    @Schema(description = "商品销售额排行（今日，前10）")
    private List<RankItem> topProducts;

    @Data
    @Schema(description = "排行项")
    public static class RankItem {
        @Schema(description = "名称（客户名/商品名）")
        private String name;
        @Schema(description = "销售额")
        private BigDecimal amount;
    }
}
