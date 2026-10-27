package com.xsy.scm.mall.home.constant;

/** 受控主题预设；自定义颜色优先于预设，由 theme_color 单独保存。 */
public enum MallTheme {
    FRESH,
    WARM,
    OCEAN;

    /** 主题编码就是枚举名；不另存字符串，避免同一份编码出现两处字面量。 */
    public String getCode() {
        return name();
    }

    public static boolean supports(String code) {
        for (MallTheme theme : values()) {
            if (theme.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
