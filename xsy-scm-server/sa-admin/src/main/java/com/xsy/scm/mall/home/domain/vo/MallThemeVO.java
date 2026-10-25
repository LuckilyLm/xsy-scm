package com.xsy.scm.mall.home.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商城当前生效主题。登录页、改密页在取得客户身份前就要初始化配色，因此只暴露主题本身， 不返回装修内容、商品与客户信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MallThemeVO {
    /** 当前发布记录 ID；无发布时为 null，由客户端使用默认主题 */
    private Long releaseId;
    /** 发布记录中的主题编码；可能为 null 或未知值，由客户端统一处理 */
    private String themeCode;
    /** 自定义主色；为空表示沿用 themeCode 对应的预设 */
    private String themeColor;
    /** 主色本身，客户端用于按钮/强调色 */
    private String primary;
    /** 深色阶，用于按压态与深色文字 */
    private String dark;
    /** 正文链接色：在白底上比主色更深 */
    private String ink;
    /** 浅色底：标签、选中背景 */
    private String light;
    /** 禁用态 */
    private String disabled;

    public MallThemeVO(Long releaseId, String themeCode) {
        this(releaseId, themeCode, null, null, null, null, null, null);
    }
}
