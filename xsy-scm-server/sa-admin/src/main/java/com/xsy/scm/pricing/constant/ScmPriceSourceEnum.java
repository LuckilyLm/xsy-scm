package com.xsy.scm.pricing.constant;

public enum ScmPriceSourceEnum {
    AGREEMENT("客户协议价"),
    CUSTOMER_TYPE("客户类型价"),
    /** 商品规格级、按时间段生效的临时价格；优先级在客户类型价之下、市场价之上。 */
    TIME_PRICE("时价"),
    MARKET("市场价");

    /** 展示名：后端导出与前端文案共用同一份定义。 */
    private final String label;

    ScmPriceSourceEnum(String label) {
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
        for (ScmPriceSourceEnum source : values()) {
            if (source.name().equals(name)) {
                return source.label;
            }
        }
        return name;
    }
}
