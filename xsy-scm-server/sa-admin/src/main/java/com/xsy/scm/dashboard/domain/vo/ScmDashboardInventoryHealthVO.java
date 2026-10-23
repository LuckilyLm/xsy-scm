package com.xsy.scm.dashboard.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页库存健康度按仓库与商品规格汇总，包含缺货和未配置阈值等状态。库存预警卡片仅统计低库存和高库存，两者口径不同。
 */
@Schema(description = "首页库存健康度五档")
public record ScmDashboardInventoryHealthVO(@Schema(description = "参与评估的 (仓库, 商品规格) 数 = 五档之和") long total,
    @Schema(description = "正常（在阈值区间内）") long normal, @Schema(description = "低于下限（补货预警，不含已缺货的）") long low,
    @Schema(description = "高于上限（积压）") long high, @Schema(description = "有余额但未配置阈值，无法判定") long unconfigured,
    @Schema(description = "缺货（可用量 ≤ 0，不依赖阈值配置）") long outOfStock) {
}
