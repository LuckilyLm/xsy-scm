package net.lab1024.sa.admin.module.scm.purchase.constant;

/**
 * 采购收货单状态（2 值）。
 *
 * <p>设计依据：W5 Target Design §4.3（Q7 / Q7a）。取值与 V15 的
 * {@code ck_purchase_receipt_status} 白名单逐字一致。
 *
 * <p>A 源的 {@code PARTIALLY_CONFIRMED} 已删除：多次确认被「一采购单多收货单」取代。
 * {@code CONFIRMED} 的判据是**本收货单是否已提交**，不由整张采购单的收货进度决定（修 A-D5）。
 */
public enum ScmReceiptStatusEnum { DRAFT, CONFIRMED }
