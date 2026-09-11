package com.xianshuyuan.scm.mall.vo;

/**
 * 跨端商城主题配置。主题只描述外观，不包含营销业务规则。
 */
public record MallThemeConfig(String themeCode, String primaryColor, String accentColor,
                              String pageBackground, Integer cardRadius, String cardStyle,
                              String productCardStyle, String navigationStyle) {
}
