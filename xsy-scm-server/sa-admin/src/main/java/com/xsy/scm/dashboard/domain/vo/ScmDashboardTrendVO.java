package com.xsy.scm.dashboard.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页趋势图：两条序列共用同一根日期轴。
 *
 * <p>
 * 主序列与次序列由 {@code metric} 决定（销售额 + 订单数 / 采购额 + 采购单数 / 入库量 + 出库量），前端据此决定 轴标签与图例文案。两条序列可能不同量纲（金额 vs 笔数），由前端用双轴表达。
 *
 * <p>
 * <b>销售与采购的末点与对应经营指标同口径</b>（销售额 + 订单数 / 采购额 + 采购单数），前端可以直接用它算环比。 <b>库存趋势不同</b>：它表达的是当日入库量与出库量，与顶部的库存预警 KPI
 * 属不同指标，别把两者对齐。
 */
@Schema(description = "首页趋势图")
public record ScmDashboardTrendVO(@Schema(description = "指标族：sales / purchase / inventory") String metric,
        @Schema(description = "区间标识：7d / 30d") String range, @Schema(description = "日期轴（MM-DD）") List<String> dates,
        @Schema(description = "主序列（金额 / 数量）") List<BigDecimal> primarySeries,
        @Schema(description = "次序列（笔数 / 数量）") List<BigDecimal> secondarySeries) {
}
