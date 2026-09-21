package net.lab1024.sa.admin.module.scm.purchase.constant;

/**
 * 入库状态（2 值，HD-B1-03）。
 *
 * <p>取值与 V22 的 {@code ck_purchase_receipt_putaway_status} 白名单逐字一致。
 *
 * <p>与 {@code purchase_receipt.status}（DRAFT/CONFIRMED 商业生命周期）**刻意解耦**：
 * {@code CONFIRMED} 只表示「收货已被确认」，不等于「库存已经入账」。
 * 入库事实由本枚举独立表达：
 * <ul>
 *   <li>{@link #PENDING}：已确认但尚未物理入库（仅 WAREHOUSE_CONFIRM）；</li>
 *   <li>{@link #COMPLETED}：已物理入库（DIRECT 在 confirm 时即完成）。</li>
 * </ul>
 */
public enum ScmPutawayStatusEnum {PENDING, COMPLETED}
