package com.xsy.scm.businesscategory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 业务分类的作用域。客户与供应商各自维护一棵树，编码和名称只在同一作用域内唯一。
 */
@Getter
@RequiredArgsConstructor
public enum BusinessCategoryScopeEnum {

    CUSTOMER("客户分类"),

    SUPPLIER("供应商分类");

    private final String desc;
}
