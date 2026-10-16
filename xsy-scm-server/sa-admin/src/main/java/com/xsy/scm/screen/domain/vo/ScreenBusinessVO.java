package com.xsy.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 大屏经营数据按今日已确认订单及结算金额汇总。订购金额与结算金额分开，排行按选定时间段取前 N 项。 */
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

    @Schema(description = "商品规格数")
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
