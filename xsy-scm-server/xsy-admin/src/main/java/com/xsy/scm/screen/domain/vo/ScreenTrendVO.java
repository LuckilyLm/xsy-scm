package com.xsy.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 大屏一次返回近 7 天或 30 天的销售、采购和库存趋势，保证各图使用同一时间区间。
 */
@Data
@Schema(description = "数据大屏-趋势数据")
public class ScreenTrendVO {

    @Schema(description = "区间标识（7d / 30d）")
    private String range;

    @Schema(description = "日期轴（MM-DD）")
    private List<String> dates;

    @Schema(description = "日期轴（YYYY-MM-DD，用于 tooltip 完整展示）")
    private List<String> fullDates;

    @Schema(description = "每日销售额（按 confirmed_at 归属，取结算金额）")
    private List<BigDecimal> sales;

    @Schema(description = "每日订单数（按 confirmed_at 归属）")
    private List<Long> orders;

    @Schema(description = "每日采购金额")
    private List<BigDecimal> purchaseAmounts;

    @Schema(description = "每日采购单数")
    private List<Long> purchaseOrders;

    @Schema(description = "每日期末库存量（累计净额，非当日变动）")
    private List<BigDecimal> inventoryQuantity;

    @Schema(description = "每日入库数量（五个入方向流水求和）")
    private List<BigDecimal> inboundQuantity;

    @Schema(description = "每日出库数量（五个出方向流水求和）")
    private List<BigDecimal> outboundQuantity;
}
