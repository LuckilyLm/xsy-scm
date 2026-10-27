package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.error.ScmErrorCode;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ScmErrorCode {
    ORDER_SUPPLEMENT_REASON_REQUIRED(40060, "补单原因不能为空"),
    ORDER_SUPPLEMENT_INVALID(40061, "非补单订单不能关联原订单或填写补单原因"),
    ORDER_SKU_DUPLICATE(40062, "订单明细中商品规格不能重复"),
    ORDER_QUANTITY_INVALID(40063, "数量必须大于零"),
    ORDER_PRICE_OVERRIDE_REASON_REQUIRED(40064, "人工改价必须同时填写价格与原因"),
    ORDER_PRICE_OVERRIDE_INVALID(40065, "非人工改价行不能指定价格"),
    ORDER_PRICE_INVALID(40066, "价格必须为非负、至多 4 位小数的定点数"),
    ORDER_ACTUAL_REASON_REQUIRED(40067, "实重修改原因不能为空"),
    ORDER_CANCEL_REASON_REQUIRED(40068, "取消原因不能为空"),
    ORDER_ITEM_VERSION_REQUIRED(40071, "保留订单明细必须携带版本"),
    ORDER_SOURCE_INVALID(40075, "后台订单来源仅允许后台录单或补单"),
    ORDER_RETURN_APPROVAL_INVALID(40072, "批准数量无效或未批准任何商品"),
    ORDER_RETURN_ITEM_INVALID(40073, "退货明细不属于原订单或订单行不可退"),
    ORDER_DELETE_STATE_INVALID(40074, "仅草稿订单可以删除"),
    ORDER_QUANTITY_FORMAT_INVALID(40076, "数量必须是整数至多 14 位、小数至多 4 位的定点数字符串"),
    ORDER_NOT_FOUND(40460, "销售订单不存在"),
    ORDER_ITEM_NOT_FOUND(40461, "订单明细不存在"),
    ORDER_RETURN_NOT_FOUND(40462, "退货单不存在"),
    ORDER_REFUND_NOT_FOUND(40463, "退款单不存在"),
    ORDER_STATE_INVALID(40960, "当前订单状态不允许此操作"),
    ORDER_ORIGINAL_INVALID(40961, "关联原订单必须已确认且客户一致"),
    ORDER_ACTUAL_NOT_ALLOWED(40962, "仅待确认订单的非标品明细可以录入实数量"),
    ORDER_ACTUAL_QUANTITY_REQUIRED(40963, "确认前所有订单明细必须具有有效实数量"),
    ORDER_ITEM_NOT_OWNED(40964, "订单明细不属于当前订单"),
    ORDER_ITEM_VERSION_CONFLICT(40965, "订单明细版本冲突"),
    ORDER_CREDIT_BLOCKED(41201, "订单确认被授信规则阻断：额度不足或存在逾期，请查看授信检查；例外放行需专门权限及原因"),
    ORDER_SETTLEMENT_METHOD_BLOCKED(41203, "商城订单结算方式当前不可用，请核对客户交易资格、货到付款授权或共享结算规则"),
    ORDER_RETURN_COST_INCOMPLETE(41202, "原销售出库成本缺失或存在多种成本，请核对出库来源后再接收"),
    ORDER_RETURN_STATUS_INVALID(40967, "当前退货状态不允许此操作"),
    ORDER_REFUND_STATUS_INVALID(40968, "当前退款状态不允许此操作"),
    ORDER_RETURN_QUANTITY_EXCEEDED(40969, "退货数量超过可退数量"),
    ORDER_RETURN_ORDER_NOT_CONFIRMED(40970, "仅已确认订单可以申请退货"),
    ORDER_ALREADY_ASSIGNED(40973, "订单已被接单或指派，请刷新待分配列表"),
    ORDER_ASSIGN_TARGET_INVALID(40974, "目标业务员不存在、已停用或没有订单查看权限"),
    ORDER_REPRICE_REQUIRED(40975, "订单价格依据已变化，请授权人员手动重算并填写原因"),
    ORDER_PENDING_EDIT_BLOCKED(40976, "待确认订单已产生支付、应收、预留、出库或分拣事实，不能再改单"),
    // 40912–40917 是 409xx 段中现查未占用的连续空位：40977–40982 与采购/商城域撞码，
    // 由 PurchaseErrorCodeTest 撞码门禁拦下后整体迁移。
    ORDER_LOCKED(40912, "订单已锁单，请新建加单而不是修改原单"),
    ORDER_EDIT_AFTER_DISPATCH_FORBIDDEN(40913, "订单已发车出库，改单需要发车后改单权限"),
    ORDER_DISCOUNT_EDIT_BLOCKED(40914, "订单已冻结优惠，不能直接改单；请取消后重下或新建加单"),
    ORDER_SORTED_ITEM_CHANGE_BLOCKED(40915, "订单已分拣或出库，不能增删商品；请新建加单"),
    ORDER_CONFIRMED_NON_STANDARD_INSERT_BLOCKED(40916, "已确认订单不能直接新增需称重的商品；请新建加单"),
    ORDER_ACTUAL_AFTER_DISPATCH_BLOCKED(40917, "订单已发车出库，不能再修改实发数量"),
    ORDER_RECEIVED_CORRECTION_INVALID(41210, "客户实收数量更正无效，请核对已出库数量与最新记录"),
    ORDER_RECEIVED_CORRECTION_CONFLICT(41211, "客户实收数量已有新记录，请刷新后再提交"),
    ORDER_EXCHANGE_NOT_READY(41212, "退货须批准并完成实物接收后才能创建换货单"),
    ORDER_EXCHANGE_EXISTS(41213, "该退货单已有换货订单"),

    /**
     * 只有已确认订单允许预留库存；草稿、取消和完成订单不得占用库存。
     */
    ORDER_RESERVE_STATE_INVALID(40945, "当前订单状态不允许预留库存（仅已确认订单可预留）");

    /**
     * 兼容别名；幂等错误码由共享 SCM 层定义。
     */
    @Deprecated
    public static final ScmErrorCode ORDER_IDEMPOTENCY_KEY_REQUIRED = ScmCommonErrorCode.IDEMPOTENCY_KEY_REQUIRED;
    @Deprecated
    public static final ScmErrorCode ORDER_IDEMPOTENCY_KEY_INVALID = ScmCommonErrorCode.IDEMPOTENCY_KEY_INVALID;
    @Deprecated
    public static final ScmErrorCode ORDER_IDEMPOTENCY_CONFLICT = ScmCommonErrorCode.IDEMPOTENCY_CONFLICT;

    private final int code;
    private final String msg;
}
