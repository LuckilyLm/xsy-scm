package com.xsy.scm.businesscategory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 客户 / 供应商业务分类错误码。与商品分类使用不同编号，避免两套分类语义互相覆盖。
 */
@Getter
@RequiredArgsConstructor
public enum BusinessCategoryErrorCode implements ScmErrorCode {

    /** 分类不存在或不属于当前作用域。 */
    BUSINESS_CATEGORY_NOT_FOUND(40444, "客户或供应商分类不存在"),

    /** 上级分类不正确：不存在、跨作用域、已停用或形成环。 */
    BUSINESS_CATEGORY_PARENT_INVALID(40044, "上级分类不正确"),

    /** 分类层级超过三级。 */
    BUSINESS_CATEGORY_LEVEL_INVALID(40045, "客户或供应商分类最多支持三级"),

    /** 作用域不合法。 */
    BUSINESS_CATEGORY_SCOPE_INVALID(40046, "分类作用域无效"),

    /** 同一作用域内分类编码重复。 */
    BUSINESS_CATEGORY_CODE_DUPLICATE(40950, "分类编码已存在"),

    /** 同一作用域内分类名称重复。 */
    BUSINESS_CATEGORY_NAME_DUPLICATE(40951, "分类名称已存在"),

    /** 分类下仍有子分类，不能删除。 */
    BUSINESS_CATEGORY_HAS_CHILDREN(40952, "分类下存在子分类，不能删除"),

    /** 分类仍被客户或供应商引用，不能删除。 */
    BUSINESS_CATEGORY_IN_USE(40953, "分类已被客户或供应商引用，不能删除"),

    /** 分类已停用，不能再被新引用。 */
    BUSINESS_CATEGORY_DISABLED(40954, "分类已停用");

    private final int code;

    private final String msg;
}
