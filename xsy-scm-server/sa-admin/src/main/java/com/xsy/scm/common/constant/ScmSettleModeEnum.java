package com.xsy.scm.common.constant;

/**
 * 客户可以独立结算或由集团统一结算。结算主体与订单负责人不同，不能混用。
 */
public enum ScmSettleModeEnum {

    INDEPENDENT("独立结算"),

    GROUP("集团结算");

    /** 展示名：导出等后端输出与前端文案共用。 */
    private final String label;

    ScmSettleModeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 枚举名转展示名；未知值原样返回，导出不因脏数据失败。 */
    public static String labelOf(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        for (ScmSettleModeEnum mode : values()) {
            if (mode.name().equals(name)) {
                return mode.label;
            }
        }
        return name;
    }
}
