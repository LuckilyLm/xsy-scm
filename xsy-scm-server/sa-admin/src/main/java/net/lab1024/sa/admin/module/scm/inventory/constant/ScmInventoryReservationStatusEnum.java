package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存预留状态。
 *
 * <pre>
 * ACTIVE ──release──▶ RELEASED     （释放：占用归还可用量）
 *    │
 *    └──consume──▶ CONSUMED        （消耗：出库时把占用转为实际扣减）
 * </pre>
 *
 * <p>与出库单状态一样**不可回退**：一旦释放或消耗，只能通过新增一条预留来表达新的占用，
 * 而不是把旧记录改回 ACTIVE —— 否则「为什么曾经释放过」这段历史会丢失。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryReservationStatusEnum {

    /**
     * 生效中：占用可用量，但未改变物理库存。
     */
    ACTIVE("生效中"),

    /**
     * 已释放：占用已归还，可用量恢复。
     */
    RELEASED("已释放"),

    /**
     * 已消耗：已被出库消费，占用转为实际扣减。
     */
    CONSUMED("已消耗");

    private final String desc;

    /**
     * 只有生效中的预留可以被释放或消耗。
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 该值是否允许写入 {@code inventory_reservation.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String value) {
        for (ScmInventoryReservationStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
