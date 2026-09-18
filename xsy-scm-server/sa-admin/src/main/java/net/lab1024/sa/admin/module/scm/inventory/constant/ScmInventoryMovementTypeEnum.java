package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存流水类型（W6 Target Design §2.2）。
 *
 * <p><b>方向编码在类型里</b>：{@code PURCHASE_IN} 即「入」，因此 {@code inventory_movement}
 * 没有独立的 {@code direction} 列，{@code quantity} 恒为正（由
 * {@code ck_inventory_movement_qty} 在 DB 层强制）。与 reference 的
 * 「direction + 正数」双表达相比少一列，且不可能自相矛盾。
 *
 * <p><b>W6-1 只有 PURCHASE_IN</b>：出库 / 调拨 / 盘点 / 报损报溢 / 规格转换
 * 全部在 W6-1 的排除清单里。新增类型必须同时：
 * <ol>
 *   <li>扩 {@code ck_inventory_movement_type} 的白名单（新迁移，不改 V19）；</li>
 *   <li>在本枚举加值；</li>
 *   <li>复用同一套「先锁单据、后按 (warehouse_id, sku_id) 升序锁余额」的锁序规则（§8.1）。</li>
 * </ol>
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryMovementTypeEnum {

    /** 采购入库：收货确认即入库（Q1 直接入库，无二次入库确认）。 */
    PURCHASE_IN("采购入库");

    /** 持久化到 {@code inventory_movement.movement_type} 的值。 */
    private final String desc;

    /** 该值是否允许写入 {@code inventory_movement.movement_type}（DB CHECK 白名单的同源判定）。 */
    public static boolean isSupported(String value) {
        for (ScmInventoryMovementTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
