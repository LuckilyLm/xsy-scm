package com.xsy.scm.pricing.constant;

public enum ScmPriceSourceEnum {
    AGREEMENT("客户协议价"),
    /** 按客户分组维护的商品单位价；优先级在逐客协议价之下、时价之上。 */
    CUSTOMER_GROUP("客户分组价"),
    /** 历史单据保留的价格来源，仅用于回显；新订单不再产生。 */
    CUSTOMER_TYPE("客户类型价"),
    /** 商品规格级、按时间段生效的临时价格；优先级在分组价之下、市场价之上。 */
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
