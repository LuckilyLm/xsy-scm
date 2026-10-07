package com.xsy.scm.dashboard.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页库存健康度五档。
 *
 * <p>
 * 五档互斥且之和恒等于 {@code total}（参与评估的「仓库 × 商品规格」数）：缺货优先于其它档且不依赖阈值配置， 「未配置」表示有余额但没配阈值、无法判定。
 *
 * <p>
 * 与顶部「库存预警」卡片<b>不是同一个指标</b>：预警列表只收「低于下限 / 高于上限」，这里还含缺货与未配置阈值。
 */
@Schema(description = "首页库存健康度五档")
public record ScmDashboardInventoryHealthVO(@Schema(description = "参与评估的 (仓库, 商品规格) 数 = 五档之和") long total,
        @Schema(description = "正常（在阈值区间内）") long normal, @Schema(description = "低于下限（补货预警，不含已缺货的）") long low,
        @Schema(description = "高于上限（积压）") long high, @Schema(description = "有余额但未配置阈值，无法判定") long unconfigured,
        @Schema(description = "缺货（可用量 ≤ 0，不依赖阈值配置）") long outOfStock) {
}
