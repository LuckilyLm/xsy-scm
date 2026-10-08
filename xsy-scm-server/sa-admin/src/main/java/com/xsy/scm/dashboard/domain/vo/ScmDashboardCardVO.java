package com.xsy.scm.dashboard.domain.vo;

import com.xsy.scm.dashboard.constant.ScmDashboardValueType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 首页 KPI 卡片：只带业务语义，不带任何展示决策。
 *
 * <p>
 * 中文文案、图标、配色、卡片样式都由前端决定；后端只回答三件事：<b>这张卡该不该给</b>（不该给就整卡不出现）、 <b>数字是多少</b>、<b>点进去看哪里</b>。把 label / color / icon 放进接口，等于把
 * UI 设计固化进 Java。
 */
@Schema(description = "首页 KPI 卡片")
public record ScmDashboardCardVO(@Schema(description = "卡片标识，前端据此映射文案与样式") String key,
        @Schema(description = "指标值") BigDecimal value, @Schema(description = "单位") ScmDashboardValueType unit,
        @Schema(description = "点击跳转的前端路由") String route) {
}
