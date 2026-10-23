package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 这里只定义采购业务的失败原因；仓库不存在、编码重复等错误码由仓库模块定义，跨模块共享不形成反向依赖。
 */
@Getter
@RequiredArgsConstructor
public enum PurchaseErrorCode implements ScmErrorCode {

    // ---- 40080–40094 BAD_REQUEST ----
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
    PURCHASE_DEMAND_BATCH_TOO_LARGE(40092, "净需求计算来源超过单批上限，请缩短时间段或增加筛选条件"),
    PURCHASE_INQUIRY_QUOTE_INVALID(40094, "询价内容、报价或有效期不符合要求"),
    PURCHASE_RECEIPT_LOT_REQUIRED(40095, "该仓库启用了批次追踪，收货必须逐行登记批次"),
    PURCHASE_RECEIPT_LOT_QUANTITY_MISMATCH(40096, "批次数量之和必须等于本次收货量"),
    PURCHASE_RECEIPT_LOT_NOT_TRACKED(40097, "该仓库未启用批次追踪，不能登记批次"),

    // ---- 40480–40488 NOT_FOUND ----
    // 40485 属仓库域、40486 属库存域（InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND），
    // 采购批次因此取 40487，避免码值冲突。
    PURCHASE_DEMAND_NOT_FOUND(40480, "采购需求不存在"),
    PURCHASE_ORDER_NOT_FOUND(40481, "采购单不存在"),
    PURCHASE_ORDER_ITEM_NOT_FOUND(40482, "采购明细不存在"),
    PURCHASE_RECEIPT_NOT_FOUND(40483, "收货单不存在"),
    PURCHASE_RECEIPT_ITEM_NOT_FOUND(40484, "收货明细不存在"),
    PURCHASE_DEMAND_BATCH_NOT_FOUND(40487, "净需求计算批次不存在"),
    PURCHASE_INQUIRY_NOT_FOUND(40488, "采购询价单不存在"),

    // ---- 40971–40999 CONFLICT ----
    PURCHASE_UNIT_CONVERSION_REQUIRED(40971, "需求单位与采购单位不一致，无法自动换算"),
    PURCHASE_DEMAND_VERSION_CONFLICT(40972, "采购需求版本冲突，请刷新后重试"),
    PURCHASE_DEMAND_SOURCE_INVALID(40980, "销售订单状态不允许生成采购需求"),
    PURCHASE_DEMAND_ALLOCATION_CONFLICT(40981, "采购需求分配冲突"),
    PURCHASE_INQUIRY_STATE_INVALID(40978, "询价已结束、截止或重复创建，无法继续操作"),
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
    PURCHASE_SUPPLIER_SKU_DISABLED(40992, "该供应商未启用此商品规格的采购配置"),
    PURCHASE_ORDER_DELETE_STATE_INVALID(40993, "仅草稿采购单可以删除"),
    PURCHASE_RECEIPT_DELETE_STATE_INVALID(40994, "仅草稿收货单可以删除"),
    PURCHASE_DEMAND_ITEM_NOT_OWNED(40995, "采购明细与采购需求不匹配"),
    PURCHASE_ORDER_ITEM_DUPLICATE_SKU(40997, "采购单内商品规格不能重复"),
    PURCHASE_RECEIPT_ITEM_INCOMPLETE(40998, "确认收货必须提交本收货单的全部明细"),
    PURCHASE_TOLERANCE_CONFIG_INVALID(40999, "采购超收容差配置无效"),

    // ---- 收货入库确认 ----
    PURCHASE_RECEIPT_PUTAWAY_STATE_INVALID(41008, "收货单当前状态不允许入库确认"),

    // ---- 采购单二维码分享 ----
    PURCHASE_SHARE_NOT_FOUND(41090, "分享链接不存在或已失效"),

    PURCHASE_SHARE_EXPIRED(41091, "分享链接已过期，请让采购员重新分享"),

    PURCHASE_SHARE_REVOKED(41092, "分享链接已被撤销"),

    PURCHASE_SHARE_STATE_INVALID(41093, "采购单当前状态不允许分享"),

    PURCHASE_SHARE_FEEDBACK_INVALID(41094, "回填的单价与重量至少填一项，且不能为负"),

    PURCHASE_SHARE_ITEM_NOT_OWNED(41095, "回填明细不属于该采购单");

    private final int code;
    private final String msg;
}
