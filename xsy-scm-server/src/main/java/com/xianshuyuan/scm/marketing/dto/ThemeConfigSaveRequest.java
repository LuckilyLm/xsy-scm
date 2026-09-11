package com.xianshuyuan.scm.marketing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ThemeConfigSaveRequest(
        @NotBlank(message = "主题编码不能为空") @Size(max = 32, message = "主题编码过长") String themeCode,
        @NotBlank(message = "主色不能为空") @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "主色必须是六位十六进制颜色") String primaryColor,
        @NotBlank(message = "强调色不能为空") @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "强调色必须是六位十六进制颜色") String accentColor,
        @NotBlank(message = "页面背景色不能为空") @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "页面背景色必须是六位十六进制颜色") String pageBackground,
        @Min(value = 0, message = "卡片圆角不能小于0") @Max(value = 32, message = "卡片圆角不能大于32") Integer cardRadius,
        @NotBlank(message = "卡片样式不能为空") @Size(max = 16, message = "卡片样式过长") String cardStyle,
        @NotBlank(message = "商品卡片样式不能为空") @Size(max = 16, message = "商品卡片样式过长") String productCardStyle,
        @NotBlank(message = "导航样式不能为空") @Size(max = 16, message = "导航样式过长") String navigationStyle
) {
}
