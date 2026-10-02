package com.xsy.scm.report.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScmFinanceProfitDimensionEnum {
    DAY("日期"),
    CUSTOMER("客户"),
    PRODUCT("商品"),
    CATEGORY("分类"),
    SELLER("销售员"),
    WAREHOUSE("仓库");

    private final String description;
}
