package com.xsy.scm.dashboard.constant;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;

/**
 * 首页趋势指标包含主副序列。每个指标需要的业务权限在服务层明确校验，避免新增指标漏做权限判断。
 */
public enum ScmDashboardTrendMetric {

    /**
     * 销售额（主，金额）+ 订单数（次，笔数）。
     */
    SALES("sales"),

    /**
     * 采购额（主，金额）+ 采购单数（次，笔数）。
     */
    PURCHASE("purchase"),

    /**
     * 入库量（主）+ 出库量（次），同一量纲可同轴比较。
     */
    INVENTORY("inventory");

    private final String code;

    ScmDashboardTrendMetric(String code) {
        this.code = code;
    }

    /**
     * 未知取值按参数不合法拒绝，不静默回落到某一个指标。
     */
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
