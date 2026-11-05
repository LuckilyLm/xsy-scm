package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品类型：是否需要按实重称量。{@code desc} 供页面、导入模板下拉与导出使用，数据库仍存枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmProductTypeEnum {

    /** 标品：按规格固定数量交易，不需要称重。 */
    STANDARD("标品"),

    /** 非标品：按实际称重计量。 */
    NON_STANDARD("非标品");

    private final String desc;
}
