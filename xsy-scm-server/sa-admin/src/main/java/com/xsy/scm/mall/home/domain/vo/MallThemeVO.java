package com.xsy.scm.mall.home.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商城当前生效主题。只返回发布指针与主题编码，供客户端在登录前初始化全局配色；草稿、装修内容、商品与客户信息都不在此接口返回。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MallThemeVO {
    /**
     * 当前发布记录 ID；无发布时为 null，由客户端使用默认主题
     */
    private Long releaseId;
    /**
     * 发布记录中的主题编码；可能为 null 或未知值，由客户端统一处理
     */
    private String themeCode;
}
