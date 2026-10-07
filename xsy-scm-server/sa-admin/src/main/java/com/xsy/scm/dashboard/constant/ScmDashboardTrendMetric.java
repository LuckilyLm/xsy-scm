package com.xsy.scm.dashboard.constant;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;

/**
 * 首页趋势图可选的指标族：每种指标对应一条主序列与一条次序列。
 *
 * <p>
 * 权限不放在这里：{@code StpUtil.checkPermission} 的参数必须是权限目录常量的直接引用， 所以「哪个指标要哪个领域权限」写在 {@code ScmDashboardService} 的 switch
 * 里，调用点一眼可见。
 */
public enum ScmDashboardTrendMetric {

    /** 销售额（主，金额）+ 订单数（次，笔数）。 */
    SALES("sales"),

    /** 采购额（主，金额）+ 采购单数（次，笔数）。 */
    PURCHASE("purchase"),

    /** 入库量（主）+ 出库量（次），同一量纲可同轴比较。 */
    INVENTORY("inventory");

    private final String code;

    ScmDashboardTrendMetric(String code) {
        this.code = code;
    }

    /** 未知取值按参数不合法拒绝，不静默回落到某一个指标。 */
    public static ScmDashboardTrendMetric parse(String code) {
        for (ScmDashboardTrendMetric metric : values()) {
            if (metric.code.equalsIgnoreCase(code)) {
                return metric;
            }
        }
        throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
    }

    public String getCode() {
        return code;
    }
}
