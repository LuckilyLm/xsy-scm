package com.xsy.scm.customer.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 客户域错误码。已有编号保持不变，新增的与定价域分开。
 */
@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements ScmErrorCode {

    /** 客户不存在。 */
    CUSTOMER_NOT_FOUND(40430, "客户不存在"),

    /** 客户类型不存在。 */
    CUSTOMER_TYPE_NOT_FOUND(40431, "客户类型不存在"),

    /** 客户状态不允许交易。 */
    CUSTOMER_NOT_TRADABLE(40930, "客户状态不可交易"),

    /** 客户编码与现有记录冲突。 */
    CUSTOMER_CODE_DUPLICATE(40936, "客户编码已存在"),

    /** 客户类型编码与现有类型冲突。 */
    CUSTOMER_TYPE_CODE_DUPLICATE(40937, "客户类型编码已存在"),

    /** 客户类型仍被客户引用。 */
    CUSTOMER_TYPE_IN_USE(40938, "该客户类型已被客户引用，不能删除"),

    /**
     * 客户仍被业务记录引用。
     *
     * <p>
     * 客户 SKU 可见性记录与销售订单分别由客户域和订单域的拦截器检查。
     */
    CUSTOMER_REFERENCED(40939, "客户已被业务数据引用，不能删除"),

    /** 上级客户不能是自身、形成环形关系或属于非集团类型。 */
    CUSTOMER_PARENT_INVALID(40032, "上级客户不正确"),

    VISIBILITY_NOT_OWNED(40932, "可见性记录不属于当前客户"),

    VISIBILITY_POLICY_CONFLICT(40033, "全部在售策略不能提交可见性明细"),

    VISIBILITY_ITEM_INVALID(40034, "可见性明细行不合法"),

    VISIBILITY_SKU_NOT_SELLABLE(40037, "可见性明细包含不可售商品规格"),

    /** 客户分组不存在。 */
    CUSTOMER_GROUP_NOT_FOUND(40434, "客户分组不存在"),

    /** 客户分组已停用，不能挂新客户或用于定价。 */
    CUSTOMER_GROUP_DISABLED(40435, "客户分组已停用"),

    /** 分组必须至少有一个客户。 */
    CUSTOMER_GROUP_MEMBER_REQUIRED(40039, "客户分组至少需要一个客户"),

    /** 分组编码与现有记录冲突。 */
    CUSTOMER_GROUP_CODE_DUPLICATE(40941, "客户分组编码已存在"),

    /** 移动或移出客户会让原分组一个客户都不剩。 */
    CUSTOMER_GROUP_LAST_MEMBER(40957, "该客户是原分组唯一成员，请先在原分组保留其他客户"),

    // 客户推广二维码
    PROMOTION_CODE_NOT_FOUND(40040, "推广码不存在或已失效"),

    PROMOTION_CODE_REVOKED(40041, "推广码已被撤销"),

    PROMOTION_CUSTOMER_NOT_FOUND(40042, "推广码所属客户不存在");

    private final int code;

    private final String msg;
}
