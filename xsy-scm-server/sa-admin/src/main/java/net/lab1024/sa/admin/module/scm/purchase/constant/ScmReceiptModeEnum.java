package net.lab1024.sa.admin.module.scm.purchase.constant;

/**
 * 收货入库方式（2 值，HD-B1-02）。
 *
 * <p>取值与 V22 的 {@code ck_purchase_receipt_mode} 白名单逐字一致。
 *
 * <ul>
 *   <li>{@link #DIRECT}：收货确认即物理入库（confirm 同事务写 PURCHASE_IN）；</li>
 *   <li>{@link #WAREHOUSE_CONFIRM}：收货确认只产生商业事实，入库由仓库二次确认（putaway）。</li>
 * </ul>
 *
 * <p>创建收货单时必须显式二选一，**没有系统默认值** —— 不允许隐藏成隐式行为。
 */
public enum ScmReceiptModeEnum { DIRECT, WAREHOUSE_CONFIRM }
