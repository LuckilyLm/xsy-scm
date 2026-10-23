package com.xsy.scm.dashboard.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页趋势数据按日期返回；销售、采购等指标各自遵守对应业务时间口径。
 */
@Schema(description = "首页趋势图")
public record ScmDashboardTrendVO(@Schema(description = "指标族：sales / purchase / inventory") String metric,
    @Schema(description = "区间标识：7d / 30d") String range, @Schema(description = "日期轴（MM-DD）") List<String> dates,
    @Schema(description = "主序列（金额 / 数量）") List<BigDecimal> primarySeries,
    @Schema(description = "次序列（笔数 / 数量）") List<BigDecimal> secondarySeries) {
}
