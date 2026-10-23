package com.xsy.scm.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 活动与优惠券模板的发布状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPromotionStatusEnum {

    /**
     * 草稿：尚未生效。
     */
    DRAFT("草稿"),

    /**
     * 进行中：已发布，客户可见可用。
     */
    ACTIVE("进行中"),

    /**
     * 已停止：不再发放或使用，历史记录保留。
     */
    STOPPED("已停止");

    private final String desc;
}
