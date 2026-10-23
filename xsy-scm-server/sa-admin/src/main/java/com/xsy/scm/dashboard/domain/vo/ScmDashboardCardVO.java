package com.xsy.scm.dashboard.domain.vo;

import com.xsy.scm.dashboard.constant.ScmDashboardValueType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 后端指标卡只返回业务值、权限可见性和跳转目标。文字、图标与颜色由前端决定，不在 Java 中硬编码 UI。
 */
@Schema(description = "首页 KPI 卡片")
public record ScmDashboardCardVO(@Schema(description = "卡片标识，前端据此映射文案与样式") String key,
    @Schema(description = "指标值") BigDecimal value, @Schema(description = "单位") ScmDashboardValueType unit,
    @Schema(description = "点击跳转的前端路由") String route) {
}
