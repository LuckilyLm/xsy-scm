package net.lab1024.sa.admin.module.scm.customer.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 客户域错误码。
 *
 * <p>码值尽量沿用 legacy（40430 / 40431 / 40930），新增码集中在 40936–40939 与 40032，
 * 避开 W3 定价域将占用的 40030 / 40031。
 */
@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements ScmErrorCode {

    /**
     * legacy：客户不存在。
     */
    CUSTOMER_NOT_FOUND(40430, "客户不存在"),

    /**
     * legacy：客户类型不存在。
     */
    CUSTOMER_TYPE_NOT_FOUND(40431, "客户类型不存在"),

    /**
     * legacy 40930：语义由「未启用」扩展为「状态不可交易」。
     */
    CUSTOMER_NOT_TRADABLE(40930, "客户状态不可交易"),

    /**
     * W2 新增：修正 legacy 靠约束名字符串匹配识别编码冲突（D4/D5）。
     */
    CUSTOMER_CODE_DUPLICATE(40936, "客户编码已存在"),

    /**
     * W2 新增：同上，客户类型编码冲突。
     */
    CUSTOMER_TYPE_CODE_DUPLICATE(40937, "客户类型编码已存在"),

    /**
     * W2 新增：删除客户类型前的引用检查。
     */
    CUSTOMER_TYPE_IN_USE(40938, "该客户类型已被客户引用，不能删除"),

    /**
     * W2 新增：删除客户前的引用检查。
     *
     * <p>W2 没有任何下游业务表引用客户，因此当前恒通过；检查位先落地，W3 接入定价 / 订单后启用。
     */
    CUSTOMER_REFERENCED(40939, "客户已被业务数据引用，不能删除"),

    /**
     * W2 新增：上级客户关系不合法（自身 / 环形 / 上级类型不是集团）。
     */
    CUSTOMER_PARENT_INVALID(40032, "上级客户不正确"),
    VISIBILITY_NOT_OWNED(40932, "可见性记录不属于当前客户"),
    VISIBILITY_POLICY_CONFLICT(40033, "全部在售策略不能提交可见性明细"),
    VISIBILITY_ITEM_INVALID(40034, "可见性明细行不合法"),
    VISIBILITY_SKU_NOT_SELLABLE(40037, "可见性明细包含不可售 SKU");

    private final int code;
    private final String msg;
}
