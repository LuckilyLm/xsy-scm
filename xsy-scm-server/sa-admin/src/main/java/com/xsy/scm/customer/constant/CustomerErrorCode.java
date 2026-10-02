package com.xsy.scm.customer.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 客户域错误码。
 *
 * <p>
 * 保留既有客户错误码 40430 / 40431 / 40930。客户专属错误使用 40936–40939 与 40032， 与定价域的 40030 / 40031 保持分离。
 */
@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements ScmErrorCode {

    /**
     * 客户不存在。
     */
    CUSTOMER_NOT_FOUND(40430, "客户不存在"),

    /**
     * 客户类型不存在。
     */
    CUSTOMER_TYPE_NOT_FOUND(40431, "客户类型不存在"),

    /**
     * 客户状态不允许交易。
     */
    CUSTOMER_NOT_TRADABLE(40930, "客户状态不可交易"),

    /**
     * 客户编码或客户类型编码与现有记录冲突。
     */
    CUSTOMER_CODE_DUPLICATE(40936, "客户编码已存在"),

    /**
     * 客户类型编码与现有类型冲突。
     */
    CUSTOMER_TYPE_CODE_DUPLICATE(40937, "客户类型编码已存在"),

    /**
     * 客户类型仍被客户引用。
     */
    CUSTOMER_TYPE_IN_USE(40938, "该客户类型已被客户引用，不能删除"),

    /**
     * 客户仍被业务记录引用。
     *
     * <p>
     * 客户 SKU 可见性记录与销售订单分别由客户域检查和订单域拦截器检查。
     */
    CUSTOMER_REFERENCED(40939, "客户已被业务数据引用，不能删除"),

    /**
     * 上级客户不能是自身、形成环形关系或属于非集团类型。
     */
    CUSTOMER_PARENT_INVALID(40032, "上级客户不正确"),

    VISIBILITY_NOT_OWNED(40932, "可见性记录不属于当前客户"),

    VISIBILITY_POLICY_CONFLICT(40033, "全部在售策略不能提交可见性明细"),

    VISIBILITY_ITEM_INVALID(40034, "可见性明细行不合法"),

    VISIBILITY_SKU_NOT_SELLABLE(40037, "可见性明细包含不可售 SKU");

    private final int code;

    private final String msg;
}
