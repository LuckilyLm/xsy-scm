package com.xsy.scm.mall.home.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import lombok.Data;

/** 商城首页草稿，顶部配色与组件一起保存并发布。 */
@Data
public class MallHomeDraftSaveForm {
    @NotNull(message = "草稿版本不能为空")
    @Min(value = 0, message = "草稿版本不能小于0")
    private Integer version;
    /**
     * 主题编码：FRESH / WARM / OCEAN。
     */
    @NotNull(message = "主题编码不能为空")
    @Size(max = 30, message = "首页最多配置30个组件")
    private String themeCode;
    /**
     * 自定义主色（#RRGGBB）；为空时沿用 themeCode 预设。
     */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "主题色必须为 #RRGGBB 格式")
    private String themeColor;

    /** 顶部起始色；不传时使用浅绿色。 */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "顶部起始色必须为 #RRGGBB 格式")
    private String headerStartColor;

    /** 顶部结束色；不传时使用白色。 */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "顶部结束色必须为 #RRGGBB 格式")
    private String headerEndColor;

    private List<@Valid MallHomeBlockForm> blocks;
}
