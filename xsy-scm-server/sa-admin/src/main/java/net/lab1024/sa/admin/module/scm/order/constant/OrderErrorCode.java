package net.lab1024.sa.admin.module.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ScmErrorCode {
    ORDER_SUPPLEMENT_REASON_REQUIRED(40060, "补单原因不能为空"),
    ORDER_SUPPLEMENT_INVALID(40061, "非补单订单不能关联原订单或填写补单原因"),
    ORDER_SKU_DUPLICATE(40062, "订单明细中 SKU 不能重复"),
    ORDER_QUANTITY_INVALID(40063, "数量必须大于零"),
    ORDER_PRICE_OVERRIDE_REASON_REQUIRED(40064, "人工改价必须同时填写价格与原因"),
    ORDER_PRICE_OVERRIDE_INVALID(40065, "非人工改价行不能指定价格"),
    ORDER_PRICE_INVALID(40066, "价格必须为非负四位定点数"),
    ORDER_ACTUAL_REASON_REQUIRED(40067, "实重修改原因不能为空"),
    ORDER_CANCEL_REASON_REQUIRED(40068, "取消原因不能为空"),
    ORDER_IDEMPOTENCY_KEY_REQUIRED(40069, "Idempotency-Key 不能为空"),
    ORDER_IDEMPOTENCY_KEY_INVALID(40070, "Idempotency-Key 长度不能超过 200 个字符"),
    ORDER_ITEM_VERSION_REQUIRED(40071, "保留订单明细必须携带版本"),
    ORDER_SOURCE_INVALID(40075, "后台订单来源仅允许后台录单或补单"),
    ORDER_RETURN_APPROVAL_INVALID(40072, "批准数量无效或未批准任何商品"),
    ORDER_RETURN_ITEM_INVALID(40073, "退货明细不属于原订单或订单行不可退"),
    ORDER_DELETE_STATE_INVALID(40074, "仅草稿订单可以删除"),
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
    ORDER_IDEMPOTENCY_CONFLICT(40966, "相同幂等键的请求内容不一致"),
    ORDER_RETURN_STATUS_INVALID(40967, "当前退货状态不允许此操作"),
    ORDER_REFUND_STATUS_INVALID(40968, "当前退款状态不允许此操作"),
    ORDER_RETURN_QUANTITY_EXCEEDED(40969, "退货数量超过可退数量"),
    ORDER_RETURN_ORDER_NOT_CONFIRMED(40970, "仅已确认订单可以申请退货"),

    /**
     * 40945：当前订单状态不允许预留库存。
     *
     * <p>只有 {@code CONFIRMED} 的订单能预留 —— 草稿还没定下要多少（实数量可能变），
     * 已取消/已完成的订单没有占用可用量的意义。
     *
     * <p>40945 已核对空闲（409xx 段在 40944 与 40946 之间有空档）。
     */
    ORDER_RESERVE_STATE_INVALID(40945, "当前订单状态不允许预留库存（仅已确认订单可预留）");
    private final int code;
    private final String msg;
}
