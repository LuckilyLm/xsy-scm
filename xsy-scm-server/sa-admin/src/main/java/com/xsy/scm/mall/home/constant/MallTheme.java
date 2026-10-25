package com.xsy.scm.mall.home.constant;

/** 受控主题预设；自定义颜色优先于预设，由 theme_color 单独保存。 */
public enum MallTheme {
    FRESH("FRESH"),
    WARM("WARM"),
    OCEAN("OCEAN");

    private final String code;

    MallTheme(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean supports(String code) {
        for (MallTheme theme : values()) {
            if (theme.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
