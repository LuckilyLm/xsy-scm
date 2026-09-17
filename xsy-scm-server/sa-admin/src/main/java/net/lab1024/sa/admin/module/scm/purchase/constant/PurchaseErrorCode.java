package net.lab1024.sa.admin.module.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 采购域错误码（38 个）。
 *
 * <p>设计依据：W5 Target Design §7.7（Q11）。与仓库域的 {@code WarehouseErrorCode}（2 个）
 * 合计 **40** 个，与 W1–W4 全部错误码**零交集**（由 {@code PurchaseErrorCodeTest} 门禁强制）。
 *
 * <pre>
 * 40080–40091 BAD_REQUEST  12
 * 40480–40484 NOT_FOUND     5
 * 40971–40972 · 40980–40995 · 40997–40999 CONFLICT  21
 * </pre>
 *
 * <p>**刻意不放进本枚举的码**：{@code WAREHOUSE_NOT_FOUND(40485)} 与
 * {@code WAREHOUSE_CODE_DUPLICATE(40996)} —— 它们属于 {@code warehouse} 域，
 * 放在这里会让 warehouse 域反向依赖 purchase 域。{@code PURCHASE_WAREHOUSE_DISABLED(40987)}
 * **留在本枚举**：它是**采购侧规则**（不允许用停用仓库建单），不是仓库域自身的不变量。
 *
 * <p>复用（不重复定义）：{@code ScmCommonErrorCode.VERSION_CONFLICT(40921)} ·
 * {@code ScmCommonErrorCode.VALIDATION_ERROR(40000)}。
 */
@Getter
@RequiredArgsConstructor
public enum PurchaseErrorCode implements ScmErrorCode {

    // ---- 40080–40091 BAD_REQUEST ----
    PURCHASE_QUANTITY_INVALID(40080, "采购数量必须为大于零的四位定点数"),
    PURCHASE_PRICE_INVALID(40081, "采购单价必须为非负四位定点数"),
    PURCHASE_DEMAND_ALLOCATION_EXCEEDED(40082, "分配数量超过需求量"),
    PURCHASE_RECEIPT_QUANTITY_INVALID(40083, "收货数量或实重不正确"),
    PURCHASE_IDEMPOTENCY_KEY_REQUIRED(40084, "Idempotency-Key 不能为空"),
    PURCHASE_IDEMPOTENCY_KEY_INVALID(40085, "Idempotency-Key 长度不能超过 200 个字符"),
    PURCHASE_CANCEL_REASON_REQUIRED(40086, "取消原因不能为空"),
    PURCHASE_SHORT_CLOSE_REASON_REQUIRED(40087, "少收关单原因不能为空"),
    PURCHASE_ITEM_VERSION_REQUIRED(40088, "保留采购明细必须携带版本"),
    PURCHASE_ORDER_ITEM_EMPTY(40089, "采购单至少需要一行有效明细"),
    PURCHASE_DEMAND_ALLOCATION_DUPLICATE(40090, "同一采购行重复关联同一采购需求"),
    PURCHASE_DEMAND_VERSION_REQUIRED(40091, "采购需求分配必须携带需求版本"),

    // ---- 40480–40484 NOT_FOUND ----
    PURCHASE_DEMAND_NOT_FOUND(40480, "采购需求不存在"),
    PURCHASE_ORDER_NOT_FOUND(40481, "采购单不存在"),
    PURCHASE_ORDER_ITEM_NOT_FOUND(40482, "采购明细不存在"),
    PURCHASE_RECEIPT_NOT_FOUND(40483, "收货单不存在"),
    PURCHASE_RECEIPT_ITEM_NOT_FOUND(40484, "收货明细不存在"),

    // ---- 40971–40999 CONFLICT ----
    PURCHASE_UNIT_CONVERSION_REQUIRED(40971, "需求单位与采购单位不一致，W5 不支持自动换算"),
    PURCHASE_DEMAND_VERSION_CONFLICT(40972, "采购需求版本冲突，请刷新后重试"),
    PURCHASE_DEMAND_SOURCE_INVALID(40980, "销售订单状态不允许生成采购需求"),
    PURCHASE_DEMAND_ALLOCATION_CONFLICT(40981, "采购需求分配冲突"),
    PURCHASE_ORDER_STATE_INVALID(40982, "当前采购单状态不允许此操作"),
    PURCHASE_ORDER_ITEM_NOT_OWNED(40983, "采购明细不属于当前采购单"),
    PURCHASE_ORDER_ITEM_VERSION_CONFLICT(40984, "采购明细版本冲突"),
    PURCHASE_DEMAND_REPLACEMENT_NOT_ALLOWED(40985, "保留采购明细不允许替换采购需求来源"),
    PURCHASE_SUPPLIER_DISABLED(40986, "供应商已停用，不能用于新采购单"),
    PURCHASE_WAREHOUSE_DISABLED(40987, "仓库已停用，不能用于新采购单"),
    PURCHASE_RECEIPT_STATE_INVALID(40988, "当前收货单状态不允许此操作"),
    PURCHASE_RECEIPT_OVER_RECEIVED(40989, "本次收货数量超过剩余可收数量"),
    PURCHASE_IDEMPOTENCY_CONFLICT(40990, "相同幂等键的请求内容不一致"),
    PURCHASE_RECEIPT_ORDER_STATE_INVALID(40991, "当前采购单状态不允许创建或确认收货"),
    PURCHASE_SUPPLIER_SKU_DISABLED(40992, "该供应商未启用此 SKU 的采购配置"),
    PURCHASE_ORDER_DELETE_STATE_INVALID(40993, "仅草稿采购单可以删除"),
    PURCHASE_RECEIPT_DELETE_STATE_INVALID(40994, "仅草稿收货单可以删除"),
    PURCHASE_DEMAND_ITEM_NOT_OWNED(40995, "采购明细与采购需求不匹配"),
    PURCHASE_ORDER_ITEM_DUPLICATE_SKU(40997, "采购单内 SKU 不能重复"),
    PURCHASE_RECEIPT_ITEM_INCOMPLETE(40998, "确认收货必须提交本收货单的全部明细"),
    PURCHASE_TOLERANCE_CONFIG_INVALID(40999, "采购超收容差配置无效");

    private final int code;
    private final String msg;
}
