package com.xsy.scm.dashboard.constant;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;

/**
 * 首页排行的维度，两个维度都来自销售数据。
 *
 * <p>
 * 权限校验不放在这里：{@code StpUtil.checkPermission} 的参数必须是权限常量的直接引用，所以写在 {@code ScmDashboardService} 的 switch 里。
 */
public enum ScmDashboardRankDimension {

    CUSTOMER("customer"),

    PRODUCT("product");

    private final String code;

    ScmDashboardRankDimension(String code) {
        this.code = code;
    }

    /** 未知取值按参数不合法拒绝，不静默回落。 */
    public static ScmDashboardRankDimension parse(String code) {
        for (ScmDashboardRankDimension dimension : values()) {
            if (dimension.code.equalsIgnoreCase(code)) {
                return dimension;
            }
        }
        throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
    }

    public String getCode() {
        return code;
    }
}
